// Re-export from crypto crate
pub use crypto::frost::{
    dkg::{KeygenError, SharePackage},
    KeyCommitments, ShareDetails,
};
use serde::{Deserialize, Serialize};

use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use std::sync::Mutex;

use crypto::frost::dkg::{aggregate_shares, equality_check, generate_share_packages};
use crypto::frost::Participant;

#[derive(Serialize)]
pub struct InitiateDistributedKeygenAppRequest {
    pub app_share_package: SharePackage,
}

#[derive(Serialize, Deserialize)]
pub struct InitiateDistributedKeygenServerResponse {
    pub server_share_package: SharePackage,
    pub server_key_commitments: KeyCommitments,
}

#[derive(Serialize)]
pub struct CompleteDistributedKeygenAppRequest {
    pub app_key_commitments: KeyCommitments,
}

pub struct ShareGenerator {
    inner: Mutex<ShareGeneratorState>,
}

impl Default for ShareGenerator {
    fn default() -> Self {
        Self::new()
    }
}

impl ShareGenerator {
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(ShareGeneratorState::new()),
        }
    }

    pub fn generate(&self) -> Result<String, KeygenError> {
        let mut inner = self.inner.lock().unwrap();

        let share_package = inner.generate()?;

        // TODO: shouldn't be unwrapping here, but error type is from lower-level crate.
        Ok(BASE64.encode(
            serde_json::to_vec(&InitiateDistributedKeygenAppRequest {
                app_share_package: share_package,
            })
            .unwrap(),
        ))
    }

    pub fn aggregate(&self, sealed_response: String) -> Result<ShareDetails, KeygenError> {
        let inner = self.inner.lock().unwrap();

        // TODO: shouldn't be unwrapping here, but error type is from lower-level crate.
        let unsealed_response: InitiateDistributedKeygenServerResponse =
            serde_json::from_slice(BASE64.decode(sealed_response).unwrap().as_slice()).unwrap();

        inner.aggregate(
            &unsealed_response.server_share_package,
            &unsealed_response.server_key_commitments,
        )
    }

    // TODO this is gross
    pub fn encode_complete_distribution_request(
        &self,
        share_details: ShareDetails,
    ) -> Result<String, KeygenError> {
        Ok(BASE64.encode(
            serde_json::to_vec(&CompleteDistributedKeygenAppRequest {
                app_key_commitments: share_details.key_commitments,
            })
            .unwrap(),
        ))
    }
}

struct ShareGeneratorState {
    app_share_package: Option<SharePackage>,
}

impl ShareGeneratorState {
    pub fn new() -> Self {
        Self {
            app_share_package: None,
        }
    }

    /// Returns a SharePackage to send the Server.
    fn generate(&mut self) -> Result<SharePackage, KeygenError> {
        let mut share_packages = generate_share_packages()?;

        // We always can expect the right share packages here given the above call.
        let server_share_package = share_packages.pop().expect("Missing server share package.");
        self.app_share_package = Some(share_packages.pop().expect("Missing app share package."));

        Ok(server_share_package)
    }

    /// Aggregates the peer's share package and generates the key share.
    /// MUST be run after `generate`.
    fn aggregate(
        &self,
        peer_share_package: &SharePackage,
        peer_key_commitments: &KeyCommitments,
    ) -> Result<ShareDetails, KeygenError> {
        let app_share_package = if let Some(package) = &self.app_share_package {
            package
        } else {
            return Err(KeygenError::MissingSharePackage);
        };

        let share_details =
            aggregate_shares(Participant::App, &[peer_share_package, &app_share_package])?;

        equality_check(peer_key_commitments, share_details)
    }
}

#[cfg(test)]
mod tests {
    use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
    use crypto::frost::{
        dkg::{aggregate_shares, generate_share_packages, KeygenError},
        Participant,
    };

    use crate::{InitiateDistributedKeygenServerResponse, ShareGenerator};

    #[test]
    fn test_aggregate_without_calling_generate_first() {
        let mut app_share_packages = generate_share_packages().unwrap();
        let app_share_package_for_server = app_share_packages
            .pop()
            .expect("Missing server share package.");
        let _ = app_share_packages
            .pop()
            .expect("Missing app share package.");

        let mut server_share_packages = generate_share_packages().unwrap();
        let server_share_package_for_server = server_share_packages
            .pop()
            .expect("Missing server share package.");
        let server_share_package_for_app = server_share_packages
            .pop()
            .expect("Missing server share package.");

        let share_details = aggregate_shares(
            Participant::Server,
            &[
                &app_share_package_for_server,
                &server_share_package_for_server,
            ],
        )
        .unwrap();

        let server_response = InitiateDistributedKeygenServerResponse {
            server_share_package: server_share_package_for_app,
            server_key_commitments: share_details.key_commitments,
        };

        // Attempt to aggregate with a Sharegenerator without generating a share package.
        assert_eq!(
            ShareGenerator::new()
                .aggregate(BASE64.encode(serde_json::to_vec(&server_response).unwrap())),
            Err(KeygenError::MissingSharePackage)
        )
    }
}
