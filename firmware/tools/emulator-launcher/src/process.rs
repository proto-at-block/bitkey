//! Process management for core-sim and ui-simulate.

use crate::config::Config;
use anyhow::{Context, Result};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Arc;
use tokio::sync::Mutex;
use tracing::{debug, error, info, warn};

/// Source paths for each build target (used for hash computation)
const CORE_SIM_SOURCES: &[&str] = &[
    "app/core-sim/",
    "app/meson.build",
    "config/",
    "hal/",
    "lib/",
    "mcu/",
    "meson.build",
    "python/bitkey/",
    "tasks/",
];
const UI_SOURCES: &[&str] = &["ui-simulate/"];

/// Tracks which binaries need recompilation (reason string if rebuild needed).
#[derive(Debug, Clone, Default)]
pub struct CompilationNeeds {
    pub core_sim: Option<String>,
    pub ui: Option<String>,
}

impl CompilationNeeds {
    fn any(&self) -> bool {
        self.core_sim.is_some() || self.ui.is_some()
    }
}

/// Check if a child process is still running.
fn is_alive(proc: &mut Child) -> bool {
    proc.try_wait().ok().flatten().is_none()
}

/// Kill a process and wait for it to exit.
fn kill_and_wait(proc: &mut Child) {
    let _ = proc.kill();
    let _ = proc.wait();
}

pub struct ProcessManager {
    config: Arc<Config>,
    core_sim: Mutex<Option<Child>>,
    ui_simulate: Mutex<Option<Child>>,
}

impl ProcessManager {
    pub fn new(config: Arc<Config>) -> Self {
        Self {
            config,
            core_sim: Mutex::new(None),
            ui_simulate: Mutex::new(None),
        }
    }

    pub async fn start(&self) -> Result<&'static str> {
        let mut core_sim = self.core_sim.lock().await;
        let mut ui_simulate = self.ui_simulate.lock().await;

        // Stop any running processes first (restart semantics)
        if let Some(mut proc) = ui_simulate.take() {
            info!("[core-sim] Stopping existing ui-simulate...");
            kill_and_wait(&mut proc);
        }
        if let Some(mut proc) = core_sim.take() {
            info!("[core-sim] Stopping existing core-sim...");
            kill_and_wait(&mut proc);
        }

        // Check if rebuild needed
        let needs = self.needs_compilation();

        self.rebuild_if_needed(&needs)?;

        // Start core-sim
        info!(
            "[core-sim] Starting core-sim with UI port {}...",
            self.config.ui_internal_port
        );
        let core_proc = Command::new(&self.config.core_sim_path)
            .args(["--ui-port", &self.config.ui_internal_port.to_string()])
            .env("CORE_SIM_PROVISION", "1")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::inherit()) // Show core-sim logs
            .spawn()
            .context("Failed to start core-sim")?;

        info!("[core-sim]   core-sim started (PID {})", core_proc.id());
        *core_sim = Some(core_proc);

        // Brief delay for core-sim to initialize
        tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;

        // Check for immediate crash
        if let Some(proc) = core_sim.as_mut() {
            if !is_alive(proc) {
                let exit_code = proc.try_wait().ok().flatten().and_then(|s| s.code());
                error!(
                    "[core-sim] ERROR: core-sim crashed immediately (exit code {:?})",
                    exit_code
                );
                *core_sim = None;
                return Err(anyhow::anyhow!(
                    "error:linker_failed:exit code {:?}",
                    exit_code
                ));
            }
        }

        // Start ui-simulate
        info!(
            "[core-sim] Starting ui-simulate, connecting to 127.0.0.1:{}...",
            self.config.ui_internal_port
        );
        let ui_proc = Command::new(&self.config.ui_simulate_path)
            .args([
                "--connect",
                &format!("127.0.0.1:{}", self.config.ui_internal_port),
            ])
            .stdout(Stdio::null())
            .stderr(Stdio::inherit())
            .spawn()
            .context("Failed to start ui-simulate")?;

        info!("[core-sim]   ui-simulate started (PID {})", ui_proc.id());
        *ui_simulate = Some(ui_proc);

        info!("[core-sim] ");
        info!("[core-sim] Emulator stack running:");
        info!(
            "[core-sim]   - WCA proxy: port {} -> core-sim stdin/stdout",
            self.config.wca_port
        );
        info!("[core-sim]   - core-sim: EFR32 emulator");
        info!("[core-sim]   - ui-simulate: UXC renderer");

        Ok("started")
    }

    pub async fn stop(&self) -> &'static str {
        let mut core_sim = self.core_sim.lock().await;
        let mut ui_simulate = self.ui_simulate.lock().await;

        let was_running = core_sim.is_some() || ui_simulate.is_some();

        // Stop ui-simulate first, then core-sim
        if let Some(mut proc) = ui_simulate.take() {
            debug!("[core-sim] Stopping ui-simulate...");
            kill_and_wait(&mut proc);
        }
        if let Some(mut proc) = core_sim.take() {
            debug!("[core-sim] Stopping core-sim...");
            kill_and_wait(&mut proc);
        }

        if was_running {
            "stopped"
        } else {
            "not_running"
        }
    }

    pub async fn status(&self) -> String {
        let mut core_sim = self.core_sim.lock().await;
        let mut ui_simulate = self.ui_simulate.lock().await;

        let core_alive = core_sim.as_mut().is_some_and(is_alive);
        let ui_alive = ui_simulate.as_mut().is_some_and(is_alive);

        let core_pid = core_sim.as_ref().map(|p| p.id()).unwrap_or(0);
        let ui_pid = ui_simulate.as_ref().map(|p| p.id()).unwrap_or(0);

        match (core_alive, ui_alive) {
            (true, true) => format!("running:stdio={},ui={}", core_pid, ui_pid),
            (true, false) => format!("partial:stdio={},ui=stopped", core_pid),
            (false, true) => format!("partial:stdio=stopped,ui={}", ui_pid),
            (false, false) => "not_running".to_string(),
        }
    }

    pub async fn take_core_sim_stdio(
        &self,
    ) -> Option<(std::process::ChildStdin, std::process::ChildStdout)> {
        let mut core_sim = self.core_sim.lock().await;
        let proc = core_sim.as_mut()?;
        Some((proc.stdin.take()?, proc.stdout.take()?))
    }

    /// Determines which binaries need recompilation based on source changes.
    pub fn needs_compilation(&self) -> CompilationNeeds {
        CompilationNeeds {
            core_sim: self.check_binary_needs(
                &self.config.core_sim_path,
                &self.config.core_sim_hash_path,
                CORE_SIM_SOURCES,
            ),
            ui: self.check_binary_needs(
                &self.config.ui_simulate_path,
                &self.config.ui_hash_path,
                UI_SOURCES,
            ),
        }
    }

    /// Returns Some(reason) if rebuild is needed, None otherwise.
    fn check_binary_needs(
        &self,
        binary_path: &Path,
        hash_path: &Path,
        source_paths: &[&str],
    ) -> Option<String> {
        if !binary_path.exists() {
            return Some("binary missing".to_string());
        }

        let current_hash = self.compute_hash_for_paths(source_paths)?;
        let stored_hash = std::fs::read_to_string(hash_path).ok();

        match stored_hash.as_deref().map(str::trim) {
            Some(stored) if stored == current_hash => None,
            Some(stored) => Some(format!(
                "changed ({}... -> {}...)",
                &stored[..8.min(stored.len())],
                &current_hash[..8.min(current_hash.len())]
            )),
            None => Some("no stored hash".to_string()),
        }
    }

    fn compute_hash_for_paths(&self, paths: &[&str]) -> Option<String> {
        use sha2::{Digest, Sha256};

        let files = Command::new("git")
            .args([
                "ls-files",
                "-z",
                "--cached",
                "--others",
                "--exclude-standard",
                "--",
            ])
            .args(paths)
            .current_dir(&self.config.firmware_dir)
            .output()
            .ok()?;

        if !files.status.success() {
            return None;
        }

        let mut source_files: Vec<PathBuf> = files
            .stdout
            .split(|b| *b == 0)
            .filter(|path| !path.is_empty())
            .map(|path| PathBuf::from(String::from_utf8_lossy(path).as_ref()))
            .collect();
        source_files.sort();

        let mut hasher = Sha256::new();
        for rel_path in source_files {
            hasher.update(rel_path.to_string_lossy().as_bytes());
            hasher.update([0]);

            let abs_path = self.config.firmware_dir.join(&rel_path);
            match std::fs::read(abs_path) {
                Ok(contents) => hasher.update(contents),
                Err(_) => hasher.update(b"<missing>"),
            }
            hasher.update([0]);
        }

        let result = hasher.finalize();
        Some(hex::encode(&result[..8])) // First 16 hex chars (8 bytes)
    }

    fn save_hash(&self, hash_path: &Path, hash: &str) {
        if let Some(parent) = hash_path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let _ = std::fs::write(hash_path, hash);
    }

    /// Log and rebuild binaries if needed. Returns Ok if nothing to rebuild.
    pub fn rebuild_if_needed(&self, needs: &CompilationNeeds) -> Result<()> {
        if !needs.any() {
            info!("[core-sim] Skipping rebuild: no changes");
            return Ok(());
        }

        if let Some(reason) = &needs.core_sim {
            info!("[core-sim] core-sim rebuild needed: {}", reason);
        }
        if let Some(reason) = &needs.ui {
            info!("[core-sim] ui rebuild needed: {}", reason);
        }
        self.build_stack()
    }

    fn build_stack(&self) -> Result<()> {
        info!("[core-sim] Building core-sim stack...");

        let output = Command::new("bash")
            .args([
                "-c",
                "source bin/activate-hermit && inv build.core-sim --with-ui",
            ])
            .current_dir(&self.config.firmware_dir)
            .output()
            .context("Failed to run core-sim build command")?;

        if !output.status.success() {
            error!("[core-sim] core-sim stack build failed:");
            error!("{}", String::from_utf8_lossy(&output.stderr));
            return Err(anyhow::anyhow!("core-sim stack build failed"));
        }

        if let Some(hash) = self.compute_hash_for_paths(CORE_SIM_SOURCES) {
            self.save_hash(&self.config.core_sim_hash_path, &hash);
            info!("[core-sim]   core-sim hash: {}...", &hash[..8]);
        }
        if let Some(hash) = self.compute_hash_for_paths(UI_SOURCES) {
            self.save_hash(&self.config.ui_hash_path, &hash);
            info!("[core-sim]   ui-simulate hash: {}...", &hash[..8]);
        }
        info!("[core-sim]   core-sim stack build complete");
        Ok(())
    }

    pub async fn recover_if_needed(&self) -> Result<bool> {
        let mut core_sim = self.core_sim.lock().await;
        let mut ui_simulate = self.ui_simulate.lock().await;

        if core_sim.is_none() && ui_simulate.is_none() {
            return Ok(false);
        }

        let core_alive = core_sim.as_mut().is_some_and(is_alive);
        let ui_alive = ui_simulate.as_mut().is_some_and(is_alive);

        if core_sim.is_some() && !core_alive {
            let exit_code = core_sim.as_mut().and_then(|p| p.try_wait().ok().flatten());
            warn!(
                "[core-sim][monitor] core-sim died (exit code: {:?}), restarting stack...",
                exit_code
            );

            if let Some(mut proc) = ui_simulate.take() {
                kill_and_wait(&mut proc);
            }
            *core_sim = None;

            drop(core_sim);
            drop(ui_simulate);

            tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;
            self.start().await?;
            return Ok(true);
        }

        if ui_simulate.is_some() && !ui_alive && core_alive {
            let exit_code = ui_simulate
                .as_mut()
                .and_then(|p| p.try_wait().ok().flatten());
            warn!(
                "[core-sim][monitor] ui-simulate exited (exit code: {:?}), stopping stack...",
                exit_code
            );

            if let Some(mut proc) = core_sim.take() {
                kill_and_wait(&mut proc);
            }
            *ui_simulate = None;

            info!("[core-sim][monitor] Stack stopped");
        }

        Ok(false)
    }
}
