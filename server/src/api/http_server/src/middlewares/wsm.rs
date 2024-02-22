use serde::Deserialize;

use wsm_rust_client::WsmClient;

pub use wsm_rust_client::Error;

#[derive(Deserialize)]
pub struct Config {
    pub wsm_endpoint: String,
}

impl Config {
    pub fn to_client(self) -> Result<Service, Error> {
        let client = WsmClient::new(&self.wsm_endpoint)?;
        Ok(Service { client })
    }
}

#[derive(Clone, Debug)]
pub struct Service {
    pub client: WsmClient,
}
