const DEFAULT_VSOCK_PROXY_CID: u32 = 3;
const DEFAULT_VSOCK_PROXY_PORT: u32 = 8000;

pub mod error;
pub mod http_client;
mod socket;
mod trust_anchors;
