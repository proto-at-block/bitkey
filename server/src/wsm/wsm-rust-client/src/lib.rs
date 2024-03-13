#![forbid(unsafe_code)]

extern crate core;
pub use wsm_common::derivation::WSMSupportedDomain;
pub use wsm_common::messages::api::CreatedSigningKey;

use std::fmt::Debug;

use async_trait::async_trait;
use reqwest_middleware::ClientBuilder;
use reqwest_tracing::TracingMiddleware;
use serde::{Deserialize, Serialize};
use tracing::instrument;
use url::Url;
use wsm_common::bitcoin::Network;
use wsm_common::messages::api::{
    CreateRootKeyRequest, GetIntegritySigRequest, GetIntegritySigResponse,
};

pub use wsm_common::messages::{
    TEST_DPUB_SPEND, TEST_XPUB_CONFIG, TEST_XPUB_SPEND, TEST_XPUB_SPEND_ORIGIN,
};

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error(transparent)]
    URLParse(#[from] url::ParseError),
    #[error(transparent)]
    Request(#[from] reqwest::Error),
    #[error(transparent)]
    RequestMiddleware(#[from] reqwest_middleware::Error),
    #[error("Not Implemented: {0}")]
    NotImplemented(String),
}

#[derive(Deserialize, Serialize)]
struct SignPsbtRequest {
    root_key_id: String,
    descriptor: String,
    change_descriptor: String,
    psbt: String,
}

#[derive(Deserialize, Serialize)]
pub struct SignedPsbt {
    pub psbt: String,
    pub root_key_id: String,
}

#[derive(Deserialize, Serialize)]
struct SignBlobRequest {
    root_key_id: String,
    blob: String,
}

#[derive(Deserialize, Serialize)]
pub struct SignedBlob {
    pub signature: String,
    pub root_key_id: String,
}

#[async_trait]
pub trait SigningService {
    async fn health_check(&self) -> Result<String, Error>;
    async fn create_root_key(
        &self,
        root_key_id: &str,
        network: Network,
    ) -> Result<CreatedSigningKey, Error>;
    async fn sign_psbt(
        &self,
        root_key_id: &str,
        descriptor: &str,
        change_descriptor: &str,
        psbt: &str,
    ) -> Result<SignedPsbt, Error>;
    async fn get_key_integrity_sig(
        &self,
        root_key_id: &str,
    ) -> Result<GetIntegritySigResponse, Error>;
}

#[derive(Clone)]
pub struct WsmClient {
    endpoint: reqwest::Url,
    client: reqwest_middleware::ClientWithMiddleware,
}

impl Debug for WsmClient {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("WsmClient")
            .field("endpoint", &self.endpoint.to_string())
            .finish_non_exhaustive()
    }
}

impl Default for WsmClient {
    fn default() -> Self {
        WsmClient::new("https://wsm.dev.wallet.build").expect("default client to work")
    }
}

impl WsmClient {
    pub fn new(endpoint: &str) -> Result<Self, Error> {
        Ok(WsmClient {
            endpoint: Url::parse(endpoint)?,
            client: ClientBuilder::new(reqwest::Client::new())
                .with(TracingMiddleware::default())
                .build(),
        })
    }
}

#[async_trait]
impl SigningService for WsmClient {
    #[instrument]
    async fn health_check(&self) -> Result<String, Error> {
        let res = self
            .client
            .get(self.endpoint.join("health-check")?)
            .send()
            .await?;
        Ok(res.text().await?)
    }

    #[instrument]
    async fn create_root_key(
        &self,
        root_key_id: &str,
        network: Network,
    ) -> Result<CreatedSigningKey, Error> {
        let res = self
            .client
            .post(self.endpoint.join("create-key")?)
            .json(&CreateRootKeyRequest {
                root_key_id: root_key_id.to_string(),
                network,
            })
            .send()
            .await?;
        Ok(res.json().await?)
    }

    #[instrument]
    async fn sign_psbt(
        &self,
        root_key_id: &str,
        descriptor: &str,
        change_descriptor: &str,
        psbt: &str,
    ) -> Result<SignedPsbt, Error> {
        let res = self
            .client
            .post(self.endpoint.join("sign-psbt")?)
            .json(&SignPsbtRequest {
                root_key_id: root_key_id.to_string(),
                change_descriptor: change_descriptor.to_string(),
                descriptor: descriptor.to_string(),
                psbt: psbt.to_string(),
            })
            .send()
            .await?;
        Ok(res.json().await?)
    }

    #[instrument]
    async fn get_key_integrity_sig(
        &self,
        root_key_id: &str,
    ) -> Result<GetIntegritySigResponse, Error> {
        let res = self
            .client
            .get(self.endpoint.join("integrity-sig")?)
            .json(&GetIntegritySigRequest {
                root_key_id: root_key_id.to_string(),
            })
            .send()
            .await?;
        Ok(res.json().await?)
    }
}

#[cfg(test)]
mod tests {
    use super::{TEST_DPUB_SPEND, TEST_XPUB_SPEND, TEST_XPUB_SPEND_ORIGIN};
    use crate::{SigningService, WsmClient};
    use bdk::bitcoin::psbt::PartiallySignedTransaction;

    use bdk::bitcoin::Network;
    use bdk::blockchain::ElectrumBlockchain;
    use bdk::database::MemoryDatabase;
    use bdk::electrum_client::Client;
    use bdk::wallet::AddressIndex;
    use bdk::{FeeRate, SyncOptions, Wallet};
    use std::env;
    use std::str::FromStr;
    use wsm_common::bitcoin::Network::Signet;

    use wsm_common::messages::TEST_KEY_ID;

    fn get_wsm_endpoint() -> String {
        match env::var_os("SERVER_WSM_ENDPOINT") {
            Some(v) => v.into_string().unwrap(),
            None => "http://localhost:9090".to_string(),
        }
    }

    #[tokio::test]
    async fn test_keygen() {
        let client = WsmClient::new(&get_wsm_endpoint()).unwrap();

        let root_key = client.create_root_key(TEST_KEY_ID, Signet).await.unwrap();

        assert_eq!(root_key.root_key_id, TEST_KEY_ID);
        assert_eq!(root_key.xpub, TEST_DPUB_SPEND);
    }

    async fn signing_from_descriptor_is_finalized(
        root_key_id: &str,
        descriptor: &str,
        change_descriptor: &str,
    ) {
        let wallet = Wallet::new(
            descriptor,
            Some(change_descriptor),
            Network::Signet,
            MemoryDatabase::default(),
        )
        .unwrap();
        let electrum_client = Client::new("ssl://electrum.nodes.wallet.build:51002").unwrap();
        let blockchain = ElectrumBlockchain::from(electrum_client);
        wallet.sync(&blockchain, SyncOptions::default()).unwrap();
        let send_to = wallet.get_address(AddressIndex::New).unwrap();
        let (psbt, _) = {
            let mut builder = wallet.build_tx();
            builder
                .drain_wallet() // spending the whole wallet so we don't have to manage amounts in these wallets
                .drain_to(send_to.script_pubkey())
                .enable_rbf()
                .fee_rate(FeeRate::from_sat_per_vb(1.0));
            builder.finish().unwrap()
        };
        println!("psbt: {psbt}");

        let client = WsmClient::new(&get_wsm_endpoint()).unwrap();
        let response = client
            .sign_psbt(
                root_key_id,
                descriptor,
                change_descriptor,
                &psbt.to_string(),
            )
            .await
            .unwrap();
        println!("signed psbt: {}", response.psbt);
        let signed_psbt = PartiallySignedTransaction::from_str(&response.psbt).unwrap();

        for input in signed_psbt.inputs.iter() {
            assert!(input.final_script_witness.is_some());
        }
    }

    #[tokio::test]
    async fn test_signing_with_key_should_finalize_inputs() {
        // This single-sig descriptor is from the 000.0000 wallet and has been loaded up with some signet sats.
        let good_descriptor = format!("wpkh({TEST_XPUB_SPEND_ORIGIN}{TEST_XPUB_SPEND}/0/*)");
        let good_change_descriptor = format!("wpkh({TEST_XPUB_SPEND_ORIGIN}{TEST_XPUB_SPEND}/1/*)");

        signing_from_descriptor_is_finalized(
            TEST_KEY_ID,
            &good_descriptor,
            &good_change_descriptor,
        )
        .await;
    }

    #[tokio::test]
    #[should_panic(expected = "assertion failed: input.final_script_witness.is_some()")]
    async fn test_signing_with_different_key_should_not_finalize_inputs_urn() {
        // This single-sig descriptor also has some sats, but is from a different key than tha 0000.0000 wallet
        let bad_descriptor = "wpkh([8cad9b86/84'/1'/0']tpubDDb6NX3MEhY337ri23DdPFXGdcbPWHz3gFCkqf5D1jdVKrUZWnTCXb5EBx2G2fLo6nbCLoz68UVpnZVL7qiwZSyeQAiqqkKJ2s4EZaAz15P/0/*)";
        let bad_change_descriptor = "wpkh([8cad9b86/84'/1'/0']tpubDDb6NX3MEhY337ri23DdPFXGdcbPWHz3gFCkqf5D1jdVKrUZWnTCXb5EBx2G2fLo6nbCLoz68UVpnZVL7qiwZSyeQAiqqkKJ2s4EZaAz15P/1/*)";

        signing_from_descriptor_is_finalized(TEST_KEY_ID, bad_descriptor, bad_change_descriptor)
            .await;
    }

    #[tokio::test]
    #[should_panic(expected = "assertion failed: input.final_script_witness.is_some()")]
    async fn test_signing_with_different_key_should_not_finalize_inputs_ulid() {
        // This single-sig descriptor also has some sats, but is from a different key than tha 0000.0000 wallet
        let bad_descriptor = "wpkh([8cad9b86/84'/1'/0']tpubDDb6NX3MEhY337ri23DdPFXGdcbPWHz3gFCkqf5D1jdVKrUZWnTCXb5EBx2G2fLo6nbCLoz68UVpnZVL7qiwZSyeQAiqqkKJ2s4EZaAz15P/0/*)";
        let bad_change_descriptor = "wpkh([8cad9b86/84'/1'/0']tpubDDb6NX3MEhY337ri23DdPFXGdcbPWHz3gFCkqf5D1jdVKrUZWnTCXb5EBx2G2fLo6nbCLoz68UVpnZVL7qiwZSyeQAiqqkKJ2s4EZaAz15P/1/*)";

        signing_from_descriptor_is_finalized(TEST_KEY_ID, bad_descriptor, bad_change_descriptor)
            .await;
    }
}
