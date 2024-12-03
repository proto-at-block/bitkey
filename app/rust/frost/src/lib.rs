// Re-export from crypto crate
pub use crypto::frost::{
    dkg::{KeygenError, SharePackage},
    KeyCommitments, ShareDetails,
};
use miniscript::{
    descriptor::{DescriptorXKey, Wildcard},
    DescriptorPublicKey,
};
use serde::{Deserialize, Serialize};

use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use bitcoin::{bip32::DerivationPath, psbt::Psbt, Network};
use std::sync::Mutex;

use crypto::frost::{compute_frost_master_xpub, Participant};
use crypto::frost::{
    dkg::{aggregate_shares, equality_check, generate_share_packages},
    signing::{Signer, SigningCommitment, SigningError},
    zkp::frost::FrostPartialSignature,
};

use std::str::FromStr;

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

pub struct WalletDescriptor {
    pub external: DescriptorPublicKey,
    pub change: DescriptorPublicKey,
}

pub fn compute_frost_wallet_descriptor(
    agg_public_key: bitcoin::secp256k1::PublicKey,
    network: Network,
) -> WalletDescriptor {
    let network_index = if network == Network::Bitcoin {
        "0"
    } else {
        "1"
    };

    let master_xpub = compute_frost_master_xpub(agg_public_key, network);

    let secp = bitcoin::secp256k1::Secp256k1::new();
    let external_derivation_path = DerivationPath::from_str(&format!("m/86/{}/0/0", network_index))
        .expect("Should be valid path");
    let external_xpub = master_xpub
        .derive_pub(&secp, &external_derivation_path)
        .expect("External xpub should be valid");

    let change_derivation_path = DerivationPath::from_str(&format!("m/86/{}/0/1", network_index))
        .expect("Should be valid path");
    let change_xpub = master_xpub
        .derive_pub(&secp, &change_derivation_path)
        .expect("Change xpub should be valid");

    WalletDescriptor {
        external: DescriptorPublicKey::XPub(DescriptorXKey {
            origin: Some((external_xpub.fingerprint(), external_derivation_path)),
            xkey: external_xpub,
            derivation_path: DerivationPath::default(),
            wildcard: Wildcard::Unhardened,
        }),
        change: DescriptorPublicKey::XPub(DescriptorXKey {
            origin: Some((change_xpub.fingerprint(), change_derivation_path)),
            xkey: change_xpub,
            derivation_path: DerivationPath::default(),
            wildcard: Wildcard::Unhardened,
        }),
    }
}

#[derive(Serialize)]
pub struct FrostSigningRequest {
    pub psbt: Psbt,
    pub signing_commitments: Vec<SigningCommitment>,
}

#[derive(Deserialize)]
pub struct FrostSigningResponse {
    pub signing_commitments: Vec<SigningCommitment>,
    pub partial_signatures: Vec<FrostPartialSignature>,
}

pub struct FrostSigner {
    inner: Mutex<Signer>,
}

impl FrostSigner {
    pub fn new(psbt: Psbt, share_details: ShareDetails) -> Result<Self, SigningError> {
        Ok(Self {
            inner: Mutex::new(Signer::new(Participant::App, psbt, share_details)?),
        })
    }

    pub fn sign_psbt_request(&self) -> Result<String, SigningError> {
        let signing_request = FrostSigningRequest {
            psbt: self.inner.lock().unwrap().psbt.clone(),
            signing_commitments: self.inner.lock().unwrap().public_signing_commitments(),
        };

        Ok(BASE64.encode(serde_json::to_vec(&signing_request).unwrap()))
    }

    pub fn sign_psbt(&self, sealed_response: String) -> Result<Psbt, SigningError> {
        let signing_response: FrostSigningResponse =
            serde_json::from_slice(BASE64.decode(sealed_response).unwrap().as_slice()).unwrap();
        let partial_signatures = self
            .inner
            .lock()
            .unwrap()
            .generate_partial_signatures(signing_response.signing_commitments)?;

        self.inner
            .lock()
            .unwrap()
            .sign_psbt(partial_signatures, signing_response.partial_signatures)
    }
}

#[cfg(test)]
mod tests {
    use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
    use bitcoin::{
        bip32::{DerivationPath, ExtendedPubKey},
        Network,
    };
    use crypto::frost::{
        dkg::{aggregate_shares, generate_share_packages, KeygenError},
        Participant,
    };
    use miniscript::{
        descriptor::{DescriptorXKey, Wildcard},
        DescriptorPublicKey,
    };
    use std::str::FromStr;

    use crate::{
        compute_frost_wallet_descriptor, InitiateDistributedKeygenServerResponse, ShareGenerator,
    };

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

    #[test]
    fn test_compute_frost_wallet_descriptor() {
        let agg_public_key = bitcoin::secp256k1::PublicKey::from_str(
            "02ad50e5719220146ffe4330cd2cdfe61e3316a261d72449decfdf5adeff60b5a5",
        )
        .unwrap();

        fn assert_descriptor_properties(
            descriptor: &DescriptorPublicKey,
            network_to_assert: Network,
            expected_path: &str,
        ) {
            match descriptor {
                DescriptorPublicKey::XPub(DescriptorXKey {
                    xkey: ExtendedPubKey { network, .. },
                    origin: Some((_fingerprint, origin_path)),
                    wildcard,
                    derivation_path,
                }) => {
                    assert_eq!(*network, network_to_assert);
                    assert_eq!(origin_path.to_string(), expected_path);
                    assert_eq!(*wildcard, Wildcard::Unhardened);
                    assert_eq!(*derivation_path, DerivationPath::default());
                }
                _ => panic!("Expected xpub"),
            }
        }

        // Test mainnet paths
        let mainnet_descriptor = compute_frost_wallet_descriptor(agg_public_key, Network::Bitcoin);
        assert_descriptor_properties(&mainnet_descriptor.external, Network::Bitcoin, "m/86/0/0/0");
        assert_descriptor_properties(&mainnet_descriptor.change, Network::Bitcoin, "m/86/0/0/1");

        // Test signet paths
        let signet_descriptor = compute_frost_wallet_descriptor(agg_public_key, Network::Signet);
        assert_descriptor_properties(&signet_descriptor.external, Network::Signet, "m/86/1/0/0");
        assert_descriptor_properties(&signet_descriptor.change, Network::Signet, "m/86/1/0/1");
    }
}
