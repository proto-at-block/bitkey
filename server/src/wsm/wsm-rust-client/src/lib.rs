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
    AttestationDocResponse, ContinueDistributedKeygenRequest, ContinueDistributedKeygenResponse,
    CreateRootKeyRequest, CreateSelfSovereignBackupRequest, CreateSelfSovereignBackupResponse,
    GeneratePartialSignaturesRequest, GeneratePartialSignaturesResponse, GetIntegritySigRequest,
    GetIntegritySigResponse, InitiateDistributedKeygenRequest, InitiateDistributedKeygenResponse,
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
    #[error("WSM error: {0}")]
    Wsm(String),
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
    async fn initiate_distributed_keygen(
        &self,
        root_key_id: &str,
        network: Network,
        sealed_request: &str,
    ) -> Result<InitiateDistributedKeygenResponse, Error>;
    async fn continue_distributed_keygen(
        &self,
        root_key_id: &str,
        network: Network,
        sealed_request: &str,
    ) -> Result<ContinueDistributedKeygenResponse, Error>;
    async fn generate_partial_signatures(
        &self,
        root_key_id: &str,
        network: Network,
        sealed_request: &str,
    ) -> Result<GeneratePartialSignaturesResponse, Error>;
    async fn create_self_sovereign_backup(
        &self,
        root_key_id: &str,
        network: Network,
        sealed_request: Vec<u8>,
        noise_session: Vec<u8>,
    ) -> Result<CreateSelfSovereignBackupResponse, Error>;
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
    async fn get_attestation_document(&self) -> Result<AttestationDocResponse, Error>;
}

#[derive(Clone)]
pub struct WsmClient {
    endpoint: reqwest::Url,
    client: reqwest_middleware::ClientWithMiddleware,
}

#[derive(Deserialize)]
struct WsmError {
    #[serde(rename = "error")]
    message: String,
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

    async fn handle_wsm_response<T: serde::de::DeserializeOwned>(
        &self,
        res: reqwest::Response,
    ) -> Result<T, Error> {
        if res.status().is_success() {
            Ok(res.json().await?)
        } else {
            match res.json::<WsmError>().await {
                Ok(wsm_error) => Err(Error::Wsm(wsm_error.message)),
                Err(err) => Err(Error::Wsm(err.to_string())),
            }
        }
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

        self.handle_wsm_response(res).await
    }

    #[instrument]
    async fn initiate_distributed_keygen(
        &self,
        root_key_id: &str,
        network: Network,
        sealed_request: &str,
    ) -> Result<InitiateDistributedKeygenResponse, Error> {
        let res = self
            .client
            .post(self.endpoint.join("initiate-distributed-keygen")?)
            .json(&InitiateDistributedKeygenRequest {
                root_key_id: root_key_id.to_string(),
                network,
                sealed_request: sealed_request.to_string(),
            })
            .send()
            .await?;

        self.handle_wsm_response(res).await
    }

    #[instrument]
    async fn continue_distributed_keygen(
        &self,
        root_key_id: &str,
        network: Network,
        sealed_request: &str,
    ) -> Result<ContinueDistributedKeygenResponse, Error> {
        let res = self
            .client
            .post(self.endpoint.join("continue-distributed-keygen")?)
            .json(&ContinueDistributedKeygenRequest {
                root_key_id: root_key_id.to_string(),
                network,
                sealed_request: sealed_request.to_string(),
            })
            .send()
            .await?;

        self.handle_wsm_response(res).await
    }

    #[instrument]
    async fn generate_partial_signatures(
        &self,
        root_key_id: &str,
        network: Network,
        sealed_request: &str,
    ) -> Result<GeneratePartialSignaturesResponse, Error> {
        let res = self
            .client
            .post(self.endpoint.join("generate-partial-signatures")?)
            .json(&GeneratePartialSignaturesRequest {
                root_key_id: root_key_id.to_string(),
                network,
                sealed_request: sealed_request.to_string(),
            })
            .send()
            .await?;

        self.handle_wsm_response(res).await
    }

    #[instrument]
    async fn create_self_sovereign_backup(
        &self,
        root_key_id: &str,
        network: Network,
        sealed_request: Vec<u8>,
        _noise_session: Vec<u8>,
    ) -> Result<CreateSelfSovereignBackupResponse, Error> {
        let res = self
            .client
            .post(self.endpoint.join("create-self-sovereign-backup")?)
            .json(&CreateSelfSovereignBackupRequest {
                root_key_id: root_key_id.to_string(),
                network,
                sealed_request,
                noise_session_id: "".to_string(), // TODO: extract from session once we merge stickiness logic W-10274
            })
            .send()
            .await?;

        self.handle_wsm_response(res).await
    }

    #[instrument(skip(descriptor, root_key_id, change_descriptor, psbt))]
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

        self.handle_wsm_response(res).await
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

        self.handle_wsm_response(res).await
    }

    #[instrument]
    async fn get_attestation_document(&self) -> Result<AttestationDocResponse, Error> {
        let res = self
            .client
            .get(self.endpoint.join("attestation-doc")?)
            .send()
            .await?;

        self.handle_wsm_response(res).await
    }
}

#[cfg(test)]
mod tests {
    use super::TEST_DPUB_SPEND;
    use crate::{SigningService, WsmClient};
    use bdk::bitcoin::psbt::PartiallySignedTransaction;

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
        assert_eq!(root_key.xpub, *TEST_DPUB_SPEND);
    }

    async fn signing_from_descriptor_is_finalized(
        root_key_id: &str,
        descriptor: &str,
        change_descriptor: &str,
        psbt: &str,
    ) {
        let client = WsmClient::new(&get_wsm_endpoint()).unwrap();
        let response = client
            .sign_psbt(root_key_id, descriptor, change_descriptor, psbt)
            .await
            .unwrap();
        let signed_psbt = PartiallySignedTransaction::from_str(&response.psbt).unwrap();

        for input in signed_psbt.inputs.iter() {
            assert!(input.final_script_witness.is_some());
        }
    }

    #[tokio::test]
    async fn test_signing_with_key_should_finalize_inputs() {
        let ext_descriptor = "wsh(sortedmulti(2,[a914afd8/84'/1'/0']tprv8fzcYhmRStFv8kFaTaTHeZWUjL1anYQFk6p8GJ5bGJSatkaEwrsDAqyrHrifF6uy5CpmnyfBefbvTmeyNyHFHBCzcBzzHyx4Lxi2HRQ8tFG/0/*,[a2914e58/84'/1'/0']tpubDCZ9miRrVu2bvHzQUEgWa5LTBdQC3za9hzhpm8pgnTqRyZoSEpyr8kHuNF32pHuPsKhPQnyXAERDB58ESchNDUHLKKN9hoM3YPEckcmQLir/0/*,[c345e1e9/84'/1'/0']tpubDDC5YGNGhebUAGw8nKsTCTbfutQwAXNzyATcnCsbhCjfdt2a8cpGbojfgAzPnsdsXxVypwjz2uGUV9dpWh211PeYhuHHumjRs7dgRLKcKk1/0/*))";
        let change_descriptor = "wsh(sortedmulti(2,[a914afd8/84'/1'/0']tprv8fzcYhmRStFv8kFaTaTHeZWUjL1anYQFk6p8GJ5bGJSatkaEwrsDAqyrHrifF6uy5CpmnyfBefbvTmeyNyHFHBCzcBzzHyx4Lxi2HRQ8tFG/1/*,[a2914e58/84'/1'/0']tpubDCZ9miRrVu2bvHzQUEgWa5LTBdQC3za9hzhpm8pgnTqRyZoSEpyr8kHuNF32pHuPsKhPQnyXAERDB58ESchNDUHLKKN9hoM3YPEckcmQLir/1/*,[c345e1e9/84'/1'/0']tpubDDC5YGNGhebUAGw8nKsTCTbfutQwAXNzyATcnCsbhCjfdt2a8cpGbojfgAzPnsdsXxVypwjz2uGUV9dpWh211PeYhuHHumjRs7dgRLKcKk1/1/*))";

        // We use a PSBT that already has a signature applied by one of the private keys derived using the tprv of the external descriptor
        signing_from_descriptor_is_finalized(
            TEST_KEY_ID,
            ext_descriptor,
            change_descriptor,
            "cHNidP8BAF4BAAAAAdgl/LBUglWZt7TvnCaDVGxyPazBZTXiQggzCQf+BmLEAAAAAAD9////AWxTAgAAAAAAIgAghHtLiFoUYv2/2NG2J5eCpZoOcJp/mda2wpfjnkmQ6s15CQAAAAEAtQIAAAAAAQEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP////8EAhUJAP////8CC1QCAAAAAAAiACCoM4xY2wT9qGq6YCkU4rT3a7Q9jHS36QoAy/+1k4GMOQAAAAAAAAAAJmokqiGp7eL2HD9x0d79P6mZ36NpU3VcaQaJeZlitIvr2DaXToz5ASAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABASsLVAIAAAAAACIAIKgzjFjbBP2oarpgKRTitPdrtD2MdLfpCgDL/7WTgYw5IgIDlqJO9MX8tDEeaa5UeNAapvtv+l6uJtf1JLhml2HJYotHMEQCIF1nnhlNC+KsiQgXMD4pVOmON3je+DVl9DtLCaQTdCedAiBnerFIbtKCyMmoCdnecllUfmZ5Fa51+Tp+JnmPVNtvxAEBBWlSIQIptfHA8EI7pOcS7Vb5ZNvEF8kYF1OQGOqlyfy0kRslEiECnVSxvHEzWr/gpDwVMstQqyngd6CkVplxvsDFw9nxBUwhA5aiTvTF/LQxHmmuVHjQGqb7b/peribX9SS4ZpdhyWKLU64iBgIptfHA8EI7pOcS7Vb5ZNvEF8kYF1OQGOqlyfy0kRslEhjDReHpVAAAgAEAAIAAAACAAAAAAAAAAAAiBgKdVLG8cTNav+CkPBUyy1CrKeB3oKRWmXG+wMXD2fEFTBiikU5YVAAAgAEAAIAAAACAAAAAAAAAAAAiBgOWok70xfy0MR5prlR40Bqm+2/6Xq4m1/UkuGaXYcliixipFK/YVAAAgAEAAIAAAACAAAAAAAAAAAAAAQFpUiECHloUcPenabwXIeVDsTUOkSeWPQaFercS5ebmjPvc+ishAw+m1iXFCvZG1m6Ob/H3TkVz+IaCGnEQ0Gd2i5q3qCwfIQNYFEFotMbk5sOiwKHbgdguUhgpYePgP5Ig0zuSBRq/PlOuIgICHloUcPenabwXIeVDsTUOkSeWPQaFercS5ebmjPvc+isYqRSv2FQAAIABAACAAAAAgAAAAAABAAAAIgIDD6bWJcUK9kbWbo5v8fdORXP4hoIacRDQZ3aLmreoLB8YopFOWFQAAIABAACAAAAAgAAAAAABAAAAIgIDWBRBaLTG5ObDosCh24HYLlIYKWHj4D+SINM7kgUavz4Yw0Xh6VQAAIABAACAAAAAgAAAAAABAAAAAA=="
        )
        .await;
    }

    #[tokio::test]
    #[should_panic(expected = "assertion failed: input.final_script_witness.is_some()")]
    async fn test_signing_with_different_key_should_not_finalize_inputs_urn() {
        // We generate descriptors that whose xpubs have nothing to do with the zero-ed out xpub // we use for keys generated with the root_key_id TEST_KEY_ID
        let bad_ext_descriptor = "wsh(sortedmulti(2,[06879e9d/84'/1'/0']tprv8gL9HKcPhiiHSC8fsdS9vhjii78CqZASTBzBy9ojdw6HcXCspRfwvUEbepbTF1VtQcowkgZb7gi4uKSNKEdWvGfEwH8koGX2FymhsR8HpdV/0/*,[e31f44cb/84'/1'/0']tpubDDBq6mak8FqtjjPyH44F1uQp3B2sZ3Li51jjVPPcbTUSZiFxr4VJAWfPNpbhmi1H8sGbVp4YQE7DV2mkoi4g5EsnQprFmcRxA9Xv3pd6PpT/0/*,[8cad9b86/84'/1'/0']tpubDDb6NX3MEhY337ri23DdPFXGdcbPWHz3gFCkqf5D1jdVKrUZWnTCXb5EBx2G2fLo6nbCLoz68UVpnZVL7qiwZSyeQAiqqkKJ2s4EZaAz15P/0/*))";
        let bad_change_descriptor = "wsh(sortedmulti(2,[06879e9d/84'/1'/0']tprv8gL9HKcPhiiHSC8fsdS9vhjii78CqZASTBzBy9ojdw6HcXCspRfwvUEbepbTF1VtQcowkgZb7gi4uKSNKEdWvGfEwH8koGX2FymhsR8HpdV/1/*,[e31f44cb/84'/1'/0']tpubDDBq6mak8FqtjjPyH44F1uQp3B2sZ3Li51jjVPPcbTUSZiFxr4VJAWfPNpbhmi1H8sGbVp4YQE7DV2mkoi4g5EsnQprFmcRxA9Xv3pd6PpT/1/*,[8cad9b86/84'/1'/0']tpubDDb6NX3MEhY337ri23DdPFXGdcbPWHz3gFCkqf5D1jdVKrUZWnTCXb5EBx2G2fLo6nbCLoz68UVpnZVL7qiwZSyeQAiqqkKJ2s4EZaAz15P/1/*))";

        signing_from_descriptor_is_finalized(
            TEST_KEY_ID,
            bad_ext_descriptor,
            bad_change_descriptor,
            "cHNidP8BAF4BAAAAAe+V9DNE5pn2sVr06JWZtHrXzX1vXFqTvR4sTquaEDM4AAAAAAD9////AXinBAAAAAAAIgAgBhkIc237PNE4FqG2aaLKod//lkUN7gtXxBtLnoDlQIIUCQAAAAEAtQIAAAAAAQEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP////8EArAIAP////8CF6gEAAAAAAAiACAlz8chzTZ/VoCaoSX1HuVkecXXazl0KRF/JvCoekZjmgAAAAAAAAAAJmokqiGp7eL2HD9x0d79P6mZ36NpU3VcaQaJeZlitIvr2DaXToz5ASAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABASsXqAQAAAAAACIAICXPxyHNNn9WgJqhJfUe5WR5xddrOXQpEX8m8Kh6RmOaIgID18lx1/P8JpWem+fwDoyTbh3qvJWHpJy6XmC8XJkLK1RHMEQCIDPEpXsF2xfXDwsoBCXk4aN3HDw8f+UMuCQYS3Dp6QxoAiBG+GBVaiC+45Y3hcAXYob0soK+0NvEri7n1O256ERykwEBBWlSIQI6Sqko0C7Zf1xGOR1rf/zW7Xt55nHJgsAdhznUwWUvbyECukUTuN8rDSao26u3WfKEg2iZCvaEw7FDTQB7uFY5GxwhA9fJcdfz/CaVnpvn8A6Mk24d6ryVh6Scul5gvFyZCytUU64iBgI6Sqko0C7Zf1xGOR1rf/zW7Xt55nHJgsAdhznUwWUvbxjjH0TLVAAAgAEAAIAAAACAAAAAAAAAAAAiBgK6RRO43ysNJqjbq7dZ8oSDaJkK9oTDsUNNAHu4VjkbHBiMrZuGVAAAgAEAAIAAAACAAAAAAAAAAAAiBgPXyXHX8/wmlZ6b5/AOjJNuHeq8lYeknLpeYLxcmQsrVBgGh56dVAAAgAEAAIAAAACAAAAAAAAAAAAAAQFpUiECnY1cvvifQ4H1AttcYYNVrP0ogisb+3BOttitgFy2lvAhAwLHuYt/Ib2C8c/GlCHl7pTJgB2zh0kDu3FObxffKGg9IQOpX4RYQkCDbZEW72QRPqWjNWfMEw9EvOlb4VH6DB+qRFOuIgICnY1cvvifQ4H1AttcYYNVrP0ogisb+3BOttitgFy2lvAY4x9Ey1QAAIABAACAAAAAgAAAAAABAAAAIgIDAse5i38hvYLxz8aUIeXulMmAHbOHSQO7cU5vF98oaD0YjK2bhlQAAIABAACAAAAAgAAAAAABAAAAIgIDqV+EWEJAg22RFu9kET6lozVnzBMPRLzpW+FR+gwfqkQYBoeenVQAAIABAACAAAAAgAAAAAABAAAAAA=="
        )
        .await;
    }
}
