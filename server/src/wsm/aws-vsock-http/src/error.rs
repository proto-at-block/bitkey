use thiserror::Error;

#[derive(Error, Debug)]
pub enum VsockError {
    #[error("Failed to create socket: {0}")]
    SocketCreation(#[from] nix::Error),
    #[error("Failed to connect to vsock: {0}")]
    ConnectionFailed(nix::Error),
    #[error("Connection timeout")]
    ConnectionTimeout,
    #[error("Read timeout")]
    ReadTimeout,
    #[error("HTTP request failed: {0}")]
    HttpRequest(#[from] std::io::Error),
    #[error("Invalid HTTP response: {0}")]
    InvalidResponse(String),
    #[error("TLS error: {0}")]
    TlsError(String),
}
