//! Main daemon loop and command handling.

use crate::config::Config;
use crate::process::ProcessManager;
use crate::proxy::StdioProxy;
use std::sync::Arc;
use std::thread::{self, JoinHandle};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpListener;
use tokio::sync::Mutex;
use tracing::{error, info};

/// Tracks the proxy thread and provides stop functionality.
#[derive(Default)]
struct ProxyState {
    handle: Option<JoinHandle<()>>,
    proxy: Option<Arc<StdioProxy>>,
}

impl ProxyState {
    fn stop(&mut self) {
        if let Some(proxy) = self.proxy.take() {
            proxy.stop();
        }
        if let Some(handle) = self.handle.take() {
            let _ = handle.join();
        }
    }
}

pub async fn run(config: Config) -> anyhow::Result<()> {
    let config = Arc::new(config);
    let process_manager = Arc::new(ProcessManager::new(Arc::clone(&config)));

    info!("[core-sim] Emulator launcher starting...");

    // Compile on daemon launch if needed
    let needs = process_manager.needs_compilation();
    process_manager.rebuild_if_needed(&needs)?;

    // Set up ADB forwarding
    setup_adb_forwarding(&config).await;

    // Start command server
    let listener = TcpListener::bind(("127.0.0.1", config.launcher_port)).await?;
    info!(
        "[core-sim] Launcher daemon listening on port {}",
        config.launcher_port
    );
    info!("[core-sim] Waiting for connections...");
    info!("[core-sim] (Press Ctrl+C to stop)");

    let proxy_state = Arc::new(Mutex::new(ProxyState::default()));

    // Spawn process monitor after proxy state exists so a restarted core-sim
    // always gets a fresh stdin/stdout proxy.
    let monitor_pm = process_manager.clone();
    let monitor_proxy_state = proxy_state.clone();
    let monitor_config = config.clone();
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(tokio::time::Duration::from_secs(
                monitor_config.monitor_interval_secs,
            ))
            .await;

            match monitor_pm.recover_if_needed().await {
                Ok(true) => {
                    restart_proxy(&monitor_pm, &monitor_proxy_state, monitor_config.as_ref()).await;
                }
                Ok(false) => {}
                Err(e) => error!("[core-sim][monitor] Recovery failed: {}", e),
            }
        }
    });

    loop {
        let (mut stream, addr) = listener.accept().await?;
        info!("[core-sim] Connection from {}", addr);

        let mut cmd_buf = [0u8; 16];
        let n = stream.read(&mut cmd_buf).await?;
        let cmd = String::from_utf8_lossy(&cmd_buf[..n]).trim().to_string();
        info!("[core-sim]   Command: {}", cmd);

        let result = match cmd.as_str() {
            "start" => match process_manager.start().await {
                Ok(status) => {
                    restart_proxy(&process_manager, &proxy_state, config.as_ref()).await;
                    status.to_string()
                }
                Err(e) => format!("error:{}", e),
            },
            "stop" => {
                proxy_state.lock().await.stop();
                process_manager.stop().await.to_string()
            }
            "status" => process_manager.status().await,
            _ => format!("unknown_command:{}", cmd),
        };

        info!("[core-sim]   Result: {}", result);
        let _ = stream.write_all(result.as_bytes()).await;
    }
}

async fn restart_proxy(
    process_manager: &Arc<ProcessManager>,
    proxy_state: &Arc<Mutex<ProxyState>>,
    config: &Config,
) {
    let mut state = proxy_state.lock().await;
    state.stop();

    if let Some((stdin, stdout)) = process_manager.take_core_sim_stdio().await {
        let proxy = Arc::new(StdioProxy::new(config.wca_port));
        let proxy_clone = proxy.clone();
        state.proxy = Some(proxy);
        state.handle = Some(thread::spawn(move || {
            if let Err(e) = proxy_clone.run(stdin, stdout) {
                error!("[core-sim][proxy] Proxy error: {}", e);
            }
        }));
    } else {
        error!("[core-sim][proxy] Missing core-sim stdio; WCA proxy not started");
    }
}

async fn setup_adb_forwarding(config: &Config) {
    info!("[core-sim] Setting up ADB reverse port forwarding...");

    // Remove existing reverse mappings
    let _ = tokio::process::Command::new("adb")
        .args(["reverse", "--remove-all"])
        .output()
        .await;

    // Set up reverse forwarding for both ports
    for (port, desc) in [
        (config.wca_port, "WCA proxy"),
        (config.launcher_port, "launcher"),
    ] {
        let tcp = format!("tcp:{}", port);
        let _ = tokio::process::Command::new("adb")
            .args(["reverse", &tcp, &tcp])
            .output()
            .await;
        info!(
            "[core-sim]   - Device localhost:{} -> Host localhost:{} ({})",
            port, port, desc
        );
    }
}
