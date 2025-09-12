use std::os::unix::io::{AsRawFd, BorrowedFd};
use std::{
    future::Future,
    io,
    pin::Pin,
    sync::Arc,
    task::{Context, Poll},
};

use aws_smithy_runtime_api::{
    client::{
        http::{
            HttpClient, HttpConnector, HttpConnectorFuture, HttpConnectorSettings,
            SharedHttpConnector,
        },
        orchestrator::HttpRequest,
        result::ConnectorError,
        runtime_components::RuntimeComponents,
    },
    http::{Response, StatusCode},
};
use aws_smithy_types::body::SdkBody;
use bytes::Bytes;
use http::uri::InvalidUri;
use http_body_util::{BodyExt, Full};
use hyper::{
    rt::{Read as HyperRead, Write as HyperWrite},
    Request, Uri,
};
use hyper_util::{
    client::legacy::{
        connect::{Connected, Connection},
        Client,
    },
    rt::TokioExecutor,
};
use rustls::pki_types::ServerName;
use rustls::{ClientConfig, RootCertStore};
use tokio::io::unix::AsyncFd;
use tokio::io::{AsyncRead, AsyncWrite, ReadBuf};
use tokio_rustls::{client::TlsStream, TlsConnector as TokioTlsConnector};
use tower::Service;

use crate::trust_anchors::TLS_SERVER_ROOTS;
use crate::{
    error::VsockError, socket::VsockSocket, DEFAULT_VSOCK_PROXY_CID, DEFAULT_VSOCK_PROXY_PORT,
};

/// Direct TLS-over-vsock client using tokio-rustls
/// This creates TLS connections over vsock transport and integrates with hyper
#[derive(Clone, Debug)]
pub struct TlsVsockClient {
    cid: u32,
    port: u32,
}

impl TlsVsockClient {
    /// Create a new TlsVsockClient
    ///
    /// # Arguments
    ///
    /// * `cid` - The vsock CID to connect to (default for vsock proxy is 3)
    /// * `port` - The vsock port to connect to (default for vsock proxy is 8000)
    pub fn new(cid: u32, port: u32) -> Self {
        Self { cid, port }
    }
}

impl Default for TlsVsockClient {
    fn default() -> Self {
        Self::new(DEFAULT_VSOCK_PROXY_CID, DEFAULT_VSOCK_PROXY_PORT)
    }
}

impl HttpClient for TlsVsockClient {
    fn http_connector(
        &self,
        _settings: &HttpConnectorSettings,
        _components: &RuntimeComponents,
    ) -> SharedHttpConnector {
        SharedHttpConnector::new(TlsVsockConnector::new(self.cid, self.port))
    }
}

impl HttpConnector for TlsVsockConnector {
    fn call(&self, request: HttpRequest) -> HttpConnectorFuture {
        let client = self.client.clone();

        HttpConnectorFuture::new(async move {
            let method = request.method();
            let uri = request
                .uri()
                .parse::<Uri>()
                .map_err(|e: InvalidUri| ConnectorError::other(e.into(), None))?;

            let mut hyper_request = Request::builder().method(method).uri(uri);
            for (name, value) in request.headers() {
                hyper_request = hyper_request.header(name, value);
            }

            let body = request.body().bytes().unwrap_or(&[]).to_vec();
            let hyper_request = hyper_request
                .body(Full::new(Bytes::from(body)))
                .map_err(|e: hyper::http::Error| ConnectorError::other(e.into(), None))?;

            let hyper_response = client
                .request(hyper_request)
                .await
                .map_err(|e| ConnectorError::other(e.into(), None))?;

            let status = hyper_response.status();
            let headers: Vec<_> = hyper_response
                .headers()
                .iter()
                .map(|(name, value)| (name.clone(), value.clone()))
                .collect();

            let body_bytes = hyper_response
                .into_body()
                .collect()
                .await
                .map_err(|e| ConnectorError::other(e.into(), None))?
                .to_bytes();
            let mut response = Response::new(
                StatusCode::try_from(status.as_u16())
                    .map_err(|e| ConnectorError::other(e.into(), None))?,
                SdkBody::from(body_bytes.to_vec()),
            );

            // Add headers to response
            for (name, value) in headers {
                response.headers_mut().insert(
                    name.as_str().to_string(),
                    value.to_str().unwrap_or("").to_string(),
                );
            }

            Ok(response)
        })
    }
}

#[derive(Clone)]
struct TlsVsockConnector {
    cid: u32,
    port: u32,
    client: Arc<Client<TlsVsockServiceConnector, Full<Bytes>>>,
}

impl TlsVsockConnector {
    pub fn new(cid: u32, port: u32) -> Self {
        // We only trust Amazon root CAs
        let root_store = RootCertStore {
            roots: TLS_SERVER_ROOTS.to_vec(),
        };

        let tls_config = ClientConfig::builder()
            .with_root_certificates(root_store)
            .with_no_client_auth();

        let tls_connector = TokioTlsConnector::from(Arc::new(tls_config));

        let client = Arc::new(Client::builder(TokioExecutor::new()).build(
            TlsVsockServiceConnector::new(cid, port, tls_connector.clone()),
        ));

        Self { cid, port, client }
    }
}

#[derive(Clone)]
struct TlsVsockServiceConnector {
    cid: u32,
    port: u32,
    tls_connector: TokioTlsConnector,
}

impl TlsVsockServiceConnector {
    pub fn new(cid: u32, port: u32, tls_connector: TokioTlsConnector) -> Self {
        Self {
            cid,
            port,
            tls_connector,
        }
    }
}

impl Service<Uri> for TlsVsockServiceConnector {
    type Response = TlsVsockConnection;
    type Error = VsockError;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, req: Uri) -> Self::Future {
        let cid = self.cid;
        let port = self.port;
        let tls_connector = self.tls_connector.clone();

        Box::pin(async move {
            let hostname = req
                .host()
                .ok_or_else(|| {
                    VsockError::HttpRequest(io::Error::new(
                        io::ErrorKind::InvalidInput,
                        "URI must contain a host for TLS connection",
                    ))
                })?
                .to_owned();
            let server_name = ServerName::try_from(hostname).map_err(|e| {
                VsockError::HttpRequest(io::Error::new(io::ErrorKind::InvalidInput, e))
            })?;

            let vsock_socket = VsockSocket::connect(cid, port)?;
            let vsock_stream = VsockStream::new(vsock_socket).map_err(VsockError::HttpRequest)?;

            let stream = tls_connector
                .connect(server_name, vsock_stream)
                .await
                .map_err(|e| VsockError::HttpRequest(io::Error::new(io::ErrorKind::Other, e)))?;

            Ok(TlsVsockConnection { stream })
        })
    }
}

impl std::fmt::Debug for TlsVsockConnector {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("TlsVsockConnector")
            .field("cid", &self.cid)
            .field("port", &self.port)
            .finish()
    }
}

impl std::fmt::Debug for TlsVsockServiceConnector {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("TlsVsockServiceConnector")
            .field("cid", &self.cid)
            .field("port", &self.port)
            .finish()
    }
}

struct TlsVsockConnection {
    stream: TlsStream<VsockStream>,
}

struct VsockStream {
    async_fd: AsyncFd<VsockSocket>,
}

impl VsockStream {
    fn new(socket: VsockSocket) -> io::Result<Self> {
        // Set the socket to non-blocking mode for async I/O and wrap in AsyncFd for proper async
        // notifications
        socket
            .set_nonblocking(true)
            .map_err(|e| io::Error::new(io::ErrorKind::Other, e))?;
        let async_fd = AsyncFd::new(socket)?;

        Ok(Self { async_fd })
    }
}

impl AsyncRead for VsockStream {
    fn poll_read(
        self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<io::Result<()>> {
        loop {
            // Wait for the socket to become readable
            let mut guard = match self.async_fd.poll_read_ready(cx) {
                Poll::Ready(Ok(guard)) => guard,
                Poll::Ready(Err(e)) => return Poll::Ready(Err(e)),
                Poll::Pending => return Poll::Pending,
            };

            let unfilled = buf.initialize_unfilled();
            match guard.try_io(|inner| {
                // Read directly with raw fd
                let fd = inner.as_raw_fd();

                nix::unistd::read(fd, unfilled).map_err(io::Error::from)
            }) {
                Ok(Ok(n)) => {
                    buf.advance(n);
                    return Poll::Ready(Ok(()));
                }
                Ok(Err(e)) if e.kind() == io::ErrorKind::WouldBlock => {
                    continue;
                }
                Ok(Err(e)) => return Poll::Ready(Err(e)),
                Err(_would_block) => {
                    continue;
                }
            }
        }
    }
}

impl AsyncWrite for VsockStream {
    fn poll_write(
        self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<Result<usize, io::Error>> {
        loop {
            // Wait for the socket to become writable
            let mut guard = match self.async_fd.poll_write_ready(cx) {
                Poll::Ready(Ok(guard)) => guard,
                Poll::Ready(Err(e)) => return Poll::Ready(Err(e)),
                Poll::Pending => return Poll::Pending,
            };

            // Try to write data
            match guard.try_io(|inner| {
                // Write directly with raw fd
                let fd = inner.as_raw_fd();
                let borrowed_fd = unsafe { BorrowedFd::borrow_raw(fd) };

                nix::unistd::write(borrowed_fd, buf).map_err(io::Error::from)
            }) {
                Ok(result) => return Poll::Ready(result),
                Err(_would_block) => {
                    continue;
                }
            }
        }
    }

    fn poll_flush(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<Result<(), io::Error>> {
        // VSock doesn't need explicit flushing
        Poll::Ready(Ok(()))
    }

    fn poll_shutdown(self: Pin<&mut Self>, _cx: &mut Context<'_>) -> Poll<Result<(), io::Error>> {
        Poll::Ready(Ok(()))
    }
}

impl AsyncRead for TlsVsockConnection {
    fn poll_read(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<io::Result<()>> {
        Pin::new(&mut self.stream).poll_read(cx, buf)
    }
}

impl AsyncWrite for TlsVsockConnection {
    fn poll_write(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<Result<usize, io::Error>> {
        Pin::new(&mut self.stream).poll_write(cx, buf)
    }

    fn poll_flush(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Result<(), io::Error>> {
        Pin::new(&mut self.stream).poll_flush(cx)
    }

    fn poll_shutdown(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
    ) -> Poll<Result<(), io::Error>> {
        Pin::new(&mut self.stream).poll_shutdown(cx)
    }
}

impl HyperRead for TlsVsockConnection {
    fn poll_read(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        mut buf: hyper::rt::ReadBufCursor<'_>,
    ) -> Poll<Result<(), std::io::Error>> {
        // Get the unfilled portion of the buffer
        let unfilled = buf.remaining();
        if unfilled == 0 {
            return Poll::Ready(Ok(()));
        }

        // Create a vector to read into, then copy to the cursor
        let mut temp_buf = vec![0u8; unfilled];
        let mut read_buf = ReadBuf::new(&mut temp_buf);

        match Pin::new(&mut self.stream).poll_read(cx, &mut read_buf) {
            Poll::Ready(Ok(())) => {
                let filled = read_buf.filled();
                buf.put_slice(filled);
                Poll::Ready(Ok(()))
            }
            Poll::Ready(Err(e)) => Poll::Ready(Err(e)),
            Poll::Pending => Poll::Pending,
        }
    }
}

impl HyperWrite for TlsVsockConnection {
    fn poll_write(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &[u8],
    ) -> Poll<Result<usize, std::io::Error>> {
        Pin::new(&mut self.stream).poll_write(cx, buf)
    }

    fn poll_flush(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
    ) -> Poll<Result<(), std::io::Error>> {
        Pin::new(&mut self.stream).poll_flush(cx)
    }

    fn poll_shutdown(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
    ) -> Poll<Result<(), std::io::Error>> {
        Pin::new(&mut self.stream).poll_shutdown(cx)
    }
}

impl Connection for TlsVsockConnection {
    fn connected(&self) -> Connected {
        Connected::new()
    }
}
