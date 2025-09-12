# aws-vsock-http

A Rust crate that provides HTTP/HTTPS client functionality over AWS Nitro Enclaves' vsock (virtual socket) transport. This crate enables secure communication from within AWS Nitro Enclaves to AWS services through a TLS-encrypted connection over VSOCK, which is the primary communication mechanism between enclaves and their parent EC2 instances.

## Features

- **TLS over vsock**: Secure HTTPS connections through vsock transport with TLS 1.2+ encryption
- **AWS SDK integration**: Implements the `HttpClient` trait for seamless integration with AWS SDKs
- **For use within Nitro Enclaves**: Designed specifically for the vsock communication model from within AWS Nitro Enclaves
- **Security hardening**: Only trusts AWS root certificates for TLS validation

## Usage

### Integration with AWS SDK

```rust
use aws_vsock_http::http_client::TlsVsockClient;
use aws_sdk_kms::Client;
use aws_config::{BehaviorVersion, Region};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let vsock_client = ;
    
    let config = aws_config::defaults(BehaviorVersion::latest())
        .region(Region::new("us-east-1"))
        .http_client(TlsVsockClient::default())
        .load()
        .await;
    
    // Now use the KMS client normally - requests will go through vsock
    // let response = Client::new(&config).decrypt()...
    
    Ok(())
}
```

## Connection Flow

1. When `TlsVsockClient` is created, it configures the target CID and port (defaults to CID=3, port=8000 for the VSOCK proxy running on the parent EC2 instance).
2. When the AWS SDK needs to make an HTTP request:
   1. The SDK calls `TlsVsockClient::http_connector()` to get a connector
   2. This returns a `TlsVsockConnector` wrapped in `SharedHttpConnector`
3. The `TlsVsockConnector` maintains a single `Arc<hyper::Client<...>>` 
4. When a request is made:
   - The hyper `Client` uses `TlsVsockServiceConnector` as its connection provider
   - `TlsVsockServiceConnector::call()` is invoked with the target URI
   - A new VSOCK connection is established to the proxy (CID:port)
   - The VSOCK socket is wrapped in `VsockStream` for async compatibility
5. TLS Handshake: 
   - `tokio-rustls` performs TLS negotiation over the VSOCK connection
   - The server's certificate is validated against AWS root CAs only
   - Results in a `TlsStream<VsockStream>` - TLS encryption over VSOCK transport
6. HTTP Communication:
   - The encrypted connection is wrapped in `TlsVsockConnection` that implements hyper's `Read` and `Write` traits

```
AWS SDK Request
      ↓
TlsVsockClient (implements HttpClient)
      ↓
TlsVsockConnector (holds reusable client)
      ↓
Hyper Client (connection pooling, HTTP protocol)
      ↓
TlsVsockServiceConnector (creates connections)
      ↓
VsockSocket::connect(CID=3, Port=8000)
      ↓
VsockStream (async adapter)
      ↓
TLS Handshake (tokio-rustls)
      ↓
TlsVsockConnection (encrypted channel)
      ↓
VSOCK to Parent Instance
      ↓
`vsock-proxy` on Parent
      ↓
AWS Services (KMS, Secrets Manager, etc.)
```

## Dependencies

- `nix`: Unix system calls and vsock support
- `hyper`/`hyper-util`: HTTP client functionality
- `tokio-rustls`: TLS over async streams
- `aws-smithy-*`: AWS SDK integration traits
