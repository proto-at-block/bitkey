use std::collections::HashMap;
use std::str::FromStr;

use anyhow::{anyhow, Result};
use aws_config::{BehaviorVersion, SdkConfig};
use aws_credential_types::provider::ProvideCredentials;
use aws_sdk_dynamodb::types::AttributeValue;
use bdk_wallet::bitcoin::bip32::ChildNumber;
use bdk_wallet::bitcoin::Network;
use bdk_wallet::keys::DescriptorPublicKey;
use bdk_wallet::miniscript::descriptor::{DescriptorXKey, WshInner};
use bdk_wallet::miniscript::Descriptor;
use bdk_wallet::Wallet;

const RECEIVING_PATH: [ChildNumber; 1] = [ChildNumber::Normal { index: 0 }];
const CHANGE_PATH: [ChildNumber; 1] = [ChildNumber::Normal { index: 1 }];
const DEFAULT_GAP_LIMIT: usize = 20;
const DEFAULT_ELECTRUM_SERVER: &str = "ssl://bitkey.mempool.space:50002";

#[derive(Debug)]
pub struct DebugState {
    pub environment: String,
    pub gap_limit: usize,
    pub electrum_server_url: String,
    pub ro_config: Option<SdkConfig>,
    pub ear_config: Option<SdkConfig>,
    pub account: Option<HashMap<String, AttributeValue>>,
    pub descriptors: Option<HashMap<String, String>>,
    pub wallets: HashMap<String, Wallet>,
}

impl DebugState {
    pub fn new(environment: String) -> Self {
        Self {
            environment,
            gap_limit: DEFAULT_GAP_LIMIT,
            electrum_server_url: DEFAULT_ELECTRUM_SERVER.to_string(),
            ro_config: None,  // Read-only config for DynamoDB
            ear_config: None, // Encrypted attachment reader config for DynamoDB
            account: None,
            descriptors: None,
            wallets: HashMap::new(),
        }
    }

    pub async fn get_ro_config(&mut self) -> Result<&SdkConfig> {
        if self.ro_config.is_none() {
            let profile_name = if self.environment.starts_with("prod") {
                "bitkey-production--read-only"
            } else if self.environment.starts_with("stag") {
                "bitkey-staging--read-only"
            } else {
                "bitkey-development--read-only"
            };

            let config = aws_config::defaults(BehaviorVersion::latest())
                .region("us-west-2")
                .profile_name(profile_name)
                .load()
                .await;

            // Force credential resolution here - this will trigger the shell command
            if let Some(credentials_provider) = config.credentials_provider() {
                credentials_provider
                    .provide_credentials()
                    .await
                    .map_err(|e| anyhow!("❌ Failed to resolve AWS credentials: {:?}", e))?;
            }

            self.ro_config = Some(config);
        }
        Ok(self.ro_config.as_ref().unwrap())
    }

    pub async fn get_ear_config(&mut self) -> Result<&SdkConfig> {
        if self.ear_config.is_none() {
            let profile_name = if self.environment.starts_with("prod") {
                "bitkey-production--encrypted-attachment-reader"
            } else if self.environment.starts_with("stag") {
                "bitkey-staging--encrypted-attachment-reader"
            } else {
                "bitkey-development--encrypted-attachment-reader"
            };

            let config = aws_config::defaults(BehaviorVersion::latest())
                .region("us-west-2")
                .profile_name(profile_name)
                .load()
                .await;

            // Force credential resolution here - this will trigger the shell command
            if let Some(credentials_provider) = config.credentials_provider() {
                credentials_provider
                    .provide_credentials()
                    .await
                    .map_err(|e| anyhow!("❌ Failed to resolve AWS credentials: {:?}", e))?;
            }

            self.ear_config = Some(config);
        }
        Ok(self.ear_config.as_ref().unwrap())
    }

    pub fn get_wallet(
        &mut self,
        keyset_id: &String,
        keyset: &AttributeValue,
    ) -> Result<&mut Wallet> {
        if !self.wallets.contains_key(keyset_id) {
            let Some(descriptors) = self.descriptors.as_ref() else {
                return Err(anyhow!(
                    "Descriptors not loaded. Use 'load-descriptors' first."
                ));
            };

            let descriptor_str = descriptors
                .get(keyset_id)
                .ok_or_else(|| anyhow!("Keyset ID '{}' not found in descriptors", keyset_id))?;

            let descriptor = Descriptor::<DescriptorPublicKey>::from_str(descriptor_str)?;
            let Descriptor::Wsh(wsh) = descriptor else {
                return Err(anyhow!("Descriptor is not a WSH descriptor"));
            };
            let WshInner::SortedMulti(dpks) = wsh.as_inner() else {
                return Err(anyhow!("Descriptor is not a SortedMulti descriptor"));
            };

            let descriptors = [RECEIVING_PATH, CHANGE_PATH]
                .iter()
                .map(|path| {
                    dpks.pks()
                        .iter()
                        .map(|dpk| match dpk {
                            DescriptorPublicKey::XPub(xpub) => {
                                let derivation_path = xpub.derivation_path.extend(*path);
                                DescriptorPublicKey::XPub(DescriptorXKey {
                                    derivation_path,
                                    ..xpub.clone()
                                })
                            }
                            _ => unimplemented!(),
                        })
                        .collect::<Vec<_>>()
                })
                .collect::<Vec<_>>();

            let receive_desc =
                Descriptor::new_wsh_sortedmulti(2, descriptors.first().unwrap().clone())?;
            let change_desc =
                Descriptor::new_wsh_sortedmulti(2, descriptors.get(1).unwrap().clone())?;

            let network = match keyset.to_m()?.m_get("network")?.to_s()?.as_str() {
                "bitcoin-main" => Network::Bitcoin,
                "bitcoin-signet" => Network::Signet,
                "bitcoin-testnet" => Network::Testnet,
                _ => Network::Bitcoin,
            };

            let wallet = Wallet::create(receive_desc, change_desc)
                .network(network)
                .create_wallet_no_persist()?;

            self.wallets.insert(keyset_id.to_string(), wallet);
        }

        Ok(self.wallets.get_mut(keyset_id).unwrap())
    }
}

// Trait extensions for AttributeValue
pub trait ToS {
    fn to_s(&self) -> Result<&String>;
}

impl ToS for AttributeValue {
    fn to_s(&self) -> Result<&String> {
        self.as_s()
            .map_err(|e| anyhow!("Value not stringlike {:?}", e))
    }
}

pub trait ToM {
    fn to_m(&self) -> Result<&HashMap<String, AttributeValue>>;
}

impl ToM for AttributeValue {
    fn to_m(&self) -> Result<&HashMap<String, AttributeValue>> {
        self.as_m()
            .map_err(|e| anyhow!("Value not maplike {:?}", e))
    }
}

pub trait MGet {
    fn m_get(&self, key: &str) -> Result<&AttributeValue>;
}

impl MGet for HashMap<String, AttributeValue> {
    fn m_get(&self, key: &str) -> Result<&AttributeValue> {
        self.get(key)
            .ok_or_else(|| anyhow!("Key '{}' not found in map", key))
    }
}
