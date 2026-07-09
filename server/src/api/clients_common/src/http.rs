use std::time::Duration;

use reqwest::{Client, ClientBuilder};

const TOTAL_TIMEOUT: Duration = Duration::from_secs(15);
const CONNECT_TIMEOUT: Duration = Duration::from_secs(5);

// Returns a reqwest::ClientBuilder pre-configured with default timeouts.
// Callers should use this instead of `reqwest::Client::builder()` so a wedged
// downstream connection eventually surfaces as an error rather than hanging
// forever.
pub fn default_client_builder() -> ClientBuilder {
    Client::builder()
        .timeout(TOTAL_TIMEOUT)
        .connect_timeout(CONNECT_TIMEOUT)
}

// Drop-in replacement for `reqwest::Client::new()` with default timeouts applied.
pub fn default_client() -> Client {
    default_client_builder()
        .build()
        .expect("default reqwest client should build with default config")
}
