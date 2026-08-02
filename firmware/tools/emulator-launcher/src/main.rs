//! Launcher daemon for core-sim and ui-simulate.
//!
//! Listens on port 5001, starts the firmware emulator stack when pinged.
//!
//! Architecture:
//!     SocketNfcSession (Android app)
//!          |
//!          | TCP port 5000 (WCA protocol)
//!          v
//!     [WCA Proxy]  <-- stdin/stdout -->  core-sim (EFR32 emulator)
//!                                             |
//!                                             | TCP port 9000
//!                                             v
//!                                        ui-simulate (UXC renderer)

mod config;
mod daemon;
mod process;
mod proxy;

use anyhow::Result;
use clap::Parser;
use std::net::TcpStream;
use std::path::PathBuf;
use std::time::Duration;
use tracing::{info, warn};
use tracing_subscriber::EnvFilter;

use config::Config;

#[derive(Parser)]
#[command(name = "emulator-launcher")]
#[command(about = "Launcher daemon for core-sim and ui-simulate")]
struct Args {
    /// Ensure daemon is running (for IntelliJ/Gradle before launch)
    #[arg(long)]
    ensure: bool,

    /// Path to firmware directory (defaults to parent of script location)
    #[arg(long)]
    firmware_dir: Option<PathBuf>,
}

fn main() -> Result<()> {
    // Initialize logging
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("info".parse()?))
        .with_target(false)
        .init();

    let args = Args::parse();

    // Determine firmware directory
    // Binary is at: firmware/tools/emulator-launcher/target/release/emulator-launcher
    // We need to go up 5 levels to get to firmware/
    let firmware_dir = args.firmware_dir.unwrap_or_else(|| {
        std::env::current_exe()
            .ok()
            .and_then(|p| p.ancestors().nth(5).map(PathBuf::from))
            .unwrap_or_else(|| PathBuf::from("."))
    });

    let config = Config::new(firmware_dir);

    if args.ensure {
        ensure_daemon(&config)
    } else {
        run_daemon(&config)
    }
}

fn is_daemon_running(config: &Config) -> bool {
    TcpStream::connect_timeout(
        &format!("127.0.0.1:{}", config.launcher_port)
            .parse()
            .unwrap(),
        Duration::from_secs(1),
    )
    .is_ok()
}

fn ensure_daemon(config: &Config) -> Result<()> {
    if is_daemon_running(config) {
        info!("[core-sim] Emulator launcher already running");
        return Ok(());
    }

    info!("[core-sim] Starting emulator launcher daemon...");
    fork_daemon(config)
}

#[cfg(unix)]
fn fork_daemon(config: &Config) -> Result<()> {
    use nix::unistd::{fork, setsid, ForkResult};
    use std::os::unix::io::AsRawFd;

    // First fork
    match unsafe { fork() } {
        Ok(ForkResult::Parent { .. }) => {
            // Parent waits for daemon to start listening
            for _ in 0..10 {
                std::thread::sleep(Duration::from_millis(200));
                if is_daemon_running(config) {
                    info!("[core-sim] Daemon started successfully");
                    std::process::exit(0);
                }
            }
            warn!("[core-sim] Warning: Daemon may not have started correctly");
            std::process::exit(0);
        }
        Ok(ForkResult::Child) => {
            // Child becomes session leader
            setsid()?;

            // Second fork to prevent zombie processes
            match unsafe { fork() } {
                Ok(ForkResult::Parent { .. }) => std::process::exit(0),
                Ok(ForkResult::Child) => {
                    // Grandchild runs the daemon
                    // Redirect stdin/stdout/stderr to /dev/null
                    let devnull = std::fs::OpenOptions::new()
                        .read(true)
                        .write(true)
                        .open("/dev/null")
                        .expect("Failed to open /dev/null");
                    let devnull_fd = devnull.as_raw_fd();

                    unsafe {
                        libc::dup2(devnull_fd, 0); // stdin
                        libc::dup2(devnull_fd, 1); // stdout
                        libc::dup2(devnull_fd, 2); // stderr
                    }

                    run_daemon(config)
                }
                Err(e) => {
                    eprintln!("Second fork failed: {}", e);
                    std::process::exit(1);
                }
            }
        }
        Err(e) => {
            eprintln!("First fork failed: {}", e);
            std::process::exit(1);
        }
    }
}

#[cfg(not(unix))]
fn fork_daemon(_config: &Config) -> Result<()> {
    anyhow::bail!("Daemon forking not supported on this platform")
}

fn run_daemon(config: &Config) -> Result<()> {
    // Change to firmware directory
    std::env::set_current_dir(&config.firmware_dir)?;

    let rt = tokio::runtime::Runtime::new()?;
    rt.block_on(daemon::run(config.clone()))
}
