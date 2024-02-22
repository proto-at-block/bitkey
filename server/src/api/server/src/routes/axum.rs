use std::net::SocketAddr;

use axum::Router;
use tokio::net::TcpListener;

use crate::BootstrapError;

pub async fn axum() -> Result<(TcpListener, Router), BootstrapError> {
    let bootstrap = crate::create_bootstrap(None).await?;
    let addr = SocketAddr::from((bootstrap.config.address, bootstrap.config.port));
    let listener = TcpListener::bind(addr).await?;
    Ok((listener, bootstrap.router))
}
