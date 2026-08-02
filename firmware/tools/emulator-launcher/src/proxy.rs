//! TCP-to-stdin/stdout proxy for core-sim.
//!
//! Uses synchronous I/O in threads since ChildStdin/ChildStdout
//! don't implement async traits.

use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::process::{ChildStdin, ChildStdout};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread;
use tracing::{error, info, warn};

pub struct StdioProxy {
    port: u16,
    running: Arc<AtomicBool>,
}

impl StdioProxy {
    pub fn new(port: u16) -> Self {
        Self {
            port,
            running: Arc::new(AtomicBool::new(false)),
        }
    }

    /// Run the proxy with the given stdin/stdout handles.
    /// This blocks until stopped.
    pub fn run(&self, stdin: ChildStdin, stdout: ChildStdout) -> anyhow::Result<()> {
        set_nonblocking_stdout(&stdout)?;
        let listener = TcpListener::bind(("127.0.0.1", self.port))?;
        listener.set_nonblocking(true)?;
        self.running.store(true, Ordering::SeqCst);

        info!("[core-sim]   WCA proxy listening on port {}", self.port);

        // Wrap stdin/stdout in Arc<Mutex> for sharing between threads
        let stdin = Arc::new(std::sync::Mutex::new(stdin));
        let stdout = Arc::new(std::sync::Mutex::new(stdout));

        while self.running.load(Ordering::SeqCst) {
            // Non-blocking accept with timeout
            match listener.accept() {
                Ok((stream, addr)) => {
                    info!("[core-sim][proxy] Client connected from {}", addr);

                    // Handle this connection (blocking)
                    // Only one client at a time since we share stdin/stdout
                    if let Err(e) = self.handle_connection(stream, stdin.clone(), stdout.clone()) {
                        warn!("[core-sim][proxy] Connection error: {}", e);
                    }
                    info!("[core-sim][proxy] Client disconnected");
                }
                Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                    // No connection waiting, sleep briefly
                    thread::sleep(std::time::Duration::from_millis(100));
                }
                Err(e) => {
                    error!("[core-sim][proxy] Accept error: {}", e);
                    thread::sleep(std::time::Duration::from_millis(100));
                }
            }
        }

        Ok(())
    }

    fn handle_connection(
        &self,
        mut stream: TcpStream,
        stdin: Arc<std::sync::Mutex<ChildStdin>>,
        stdout: Arc<std::sync::Mutex<ChildStdout>>,
    ) -> anyhow::Result<()> {
        stream.set_nonblocking(false)?;
        stream.set_read_timeout(Some(std::time::Duration::from_millis(100)))?;

        // Clone stream for the reader thread
        let mut stream_write = stream.try_clone()?;

        // Use a separate flag for this connection so stop() works correctly
        let conn_active = Arc::new(AtomicBool::new(true));
        let running = self.running.clone();

        // Spawn thread to read from stdout and write to socket
        let stdout_thread = {
            let conn_active = conn_active.clone();
            let running = running.clone();
            thread::spawn(move || {
                let mut buf = [0u8; 4096];
                while conn_active.load(Ordering::SeqCst) && running.load(Ordering::SeqCst) {
                    let mut guard = match stdout.lock() {
                        Ok(g) => g,
                        Err(_) => break,
                    };

                    match guard.read(&mut buf) {
                        Ok(0) => break, // EOF
                        Ok(n) => {
                            drop(guard); // Release lock before writing
                            info!("[core-sim][proxy] stdout -> socket: {} bytes", n);
                            if stream_write.write_all(&buf[..n]).is_err()
                                || stream_write.flush().is_err()
                            {
                                break;
                            }
                        }
                        Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                            drop(guard);
                            thread::sleep(std::time::Duration::from_millis(10));
                        }
                        Err(_) => break,
                    }
                }
            })
        };

        // Read from socket and write to stdin (in this thread)
        let mut buf = [0u8; 4096];
        while conn_active.load(Ordering::SeqCst) && self.running.load(Ordering::SeqCst) {
            match stream.read(&mut buf) {
                Ok(0) => break, // EOF
                Ok(n) => {
                    info!("[core-sim][proxy] socket -> stdin: {} bytes", n);
                    let mut guard = stdin.lock().map_err(|_| anyhow::anyhow!("Lock poisoned"))?;
                    if guard.write_all(&buf[..n]).is_err() || guard.flush().is_err() {
                        break;
                    }
                }
                Err(ref e)
                    if e.kind() == std::io::ErrorKind::WouldBlock
                        || e.kind() == std::io::ErrorKind::TimedOut =>
                {
                    // Timeout, check if we should keep running
                    continue;
                }
                Err(_) => break,
            }
        }

        // Signal thread to stop and wait
        conn_active.store(false, Ordering::SeqCst);
        let _ = stdout_thread.join();

        Ok(())
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
    }
}

#[cfg(unix)]
fn set_nonblocking_stdout(stdout: &ChildStdout) -> anyhow::Result<()> {
    use std::os::unix::io::AsRawFd;

    let fd = stdout.as_raw_fd();
    let flags = unsafe { libc::fcntl(fd, libc::F_GETFL) };
    if flags < 0 {
        return Err(anyhow::anyhow!("fcntl(F_GETFL) failed"));
    }
    let result = unsafe { libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK) };
    if result < 0 {
        return Err(anyhow::anyhow!("fcntl(F_SETFL) failed"));
    }
    Ok(())
}

#[cfg(not(unix))]
fn set_nonblocking_stdout(_stdout: &ChildStdout) -> anyhow::Result<()> {
    Ok(())
}
