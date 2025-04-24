use std::collections::HashMap;

use anyhow::{anyhow, Context};
use aws_sdk_dynamodb::client::Client as DdbClient;
use aws_sdk_dynamodb::types::AttributeValue;
use aws_sdk_kms::client::Client as KmsClient;
use aws_sdk_kms::types::DataKeySpec;
use base64::{engine::general_purpose::STANDARD as BASE64, Engine as _};
use tokio::sync::{Mutex, RwLock};
use tracing::{event, instrument, Level};
use ulid::Ulid;

use crate::dependencies::util::safe_get_str;

/// Limit the number of times that a DEK is used to wrap a customer signing key. This limit is rough limit.
/// We don't try for linearized consistency in keep track of how many uses the DEK has been used. We could
/// start getting into trouble around 2^32 uses (from risk of nonce-collision), so having some fudge-factor
/// around 2M keeps us _well_ within the margin of safety. Keeping a relatively low limit also reduces blast
/// radius if a DEK is ever spilled.
const MAX_DEK_USES: u32 = 2_000_000;

/// We don't want to have the API server have to check the DB everytime it goes to use a DEK to wrap a new
/// customer signing key, so we work in batches of `LEASE_SIZE` and then check the database when the "lease"
/// is up.
const LEASE_SIZE: u32 = 50;

type WrappedDekCache = HashMap<String, String>;

#[derive(Clone, Debug)]
#[allow(dead_code)]
struct LeasedDek {
    dek_id: String,
    dek_ciphertext: String,
    remaining_uses: u32,
}

#[derive(Debug)]
pub struct DekStore {
    ddb: DdbClient,
    kms: KmsClient,
    dek_table_name: String,
    kms_cmk_id: String,
    dek_cache: RwLock<WrappedDekCache>,
    current_dek: Mutex<Option<LeasedDek>>,
}

impl DekStore {
    pub fn new(ddb: DdbClient, kms: KmsClient, dek_table_name: &str, kms_cmk_id: &str) -> Self {
        DekStore {
            ddb,
            kms,
            dek_table_name: dek_table_name.to_string(),
            kms_cmk_id: kms_cmk_id.to_string(),
            dek_cache: RwLock::new(WrappedDekCache::new()),
            current_dek: Mutex::new(None),
        }
    }

    #[instrument(skip(self))]
    pub async fn get_wrapped_key(&self, dek_id: &str) -> anyhow::Result<String> {
        let cache_miss: bool;
        let dek_ciphertext = match self.dek_cache.read().await.get(dek_id) {
            // cache hit
            Some(dek_ciphertext) => {
                event!(Level::DEBUG, "local dek cache hit");
                cache_miss = false;
                dek_ciphertext.to_string()
            }
            // cache miss
            None => {
                event!(Level::DEBUG, "local dek cache miss");
                let dek = self
                    .ddb
                    .get_item()
                    .table_name(&self.dek_table_name)
                    .key("dek_id", AttributeValue::S(dek_id.to_string()))
                    .send()
                    .await?
                    .item
                    .context("No wrapped key found")?
                    .get("dek_ciphertext")
                    .context("No field called 'dek_ciphertext' in ddb")?
                    .as_s()
                    .map(String::from)
                    .map_err(|value| anyhow!("could not return {:?} as string", value))?;
                cache_miss = true;
                dek
            }
        };
        if cache_miss {
            event!(Level::DEBUG, "writing dek to local cache");
            self.dek_cache
                .write()
                .await
                .insert(dek_id.to_string(), dek_ciphertext.clone());
        }
        Ok(dek_ciphertext)
    }

    /// Get or create a Data-Encryption Key (DEK) that has been used for fewer than MAX_DEK_USES customer keys
    /// If there is a DEK in DDB with the `isAvailable` attribute set to 1 (true), then bump the number
    /// of times that it might have been used (we want a DEK to be used *fewer* than  about MAX_DEK_USES times, we
    /// don't care if we over-count, we also don't need it to be super precise. Some double counting is ok)
    /// and get that DEK. If there is not an available DEK in DDB, then use KMS to create a new one, and store it in DDB.
    /// Note: We are *not* trying to prevent two servers from using the same DEK. That's OK!
    #[instrument(skip(self))]
    async fn get_or_create_dek(&self) -> anyhow::Result<LeasedDek> {
        // Look for any key that has `isAvailable` set to 1.
        let available_key_qo = self
            .ddb
            .query()
            .table_name(self.dek_table_name.clone())
            .index_name("availableKeysIdx")
            .key_condition_expression("#hk = :t")
            .expression_attribute_names("#hk", "isAvailable")
            .expression_attribute_values(":t", AttributeValue::N("1".to_string()))
            .limit(1)
            .send()
            .await
            .context("could not query DEK table")?;
        let available_key_rs = available_key_qo.items(); // items() will return Some(&[]) if the set is empty. treat that and None (which is a null) the same
        match available_key_rs.first() {
            None => {
                // No available key found in DDB, create a new one
                event!(Level::INFO, "No DEK available for use");
                let dek_ciphertext = BASE64.encode(
                    self.kms
                        .generate_data_key_without_plaintext()
                        .set_key_id(Some(self.kms_cmk_id.clone()))
                        .key_spec(DataKeySpec::Aes256)
                        .send()
                        .await
                        .context("could not call KMS to generate fresh data key")?
                        .ciphertext_blob
                        .unwrap()
                        .into_inner(),
                );
                // Pick a random dek_id
                let dek_id = Ulid::new().to_string();
                // Load the new key into DDB
                event!(Level::INFO, "Inserting new DEK with ID {} into DDB", dek_id);
                self.ddb
                    .put_item()
                    .table_name(self.dek_table_name.clone())
                    .item("dek_id", AttributeValue::S(dek_id.clone()))
                    .item("dek_ciphertext", AttributeValue::S(dek_ciphertext.clone()))
                    .item("usage_count", AttributeValue::N(LEASE_SIZE.to_string()))
                    .item("isAvailable", AttributeValue::N("1".to_string()))
                    .send()
                    .await
                    .context("could not write new DEK to DDB")?;
                Ok(LeasedDek {
                    dek_id,
                    dek_ciphertext,
                    remaining_uses: LEASE_SIZE,
                })
            }
            Some(item) => {
                let dek_id = item
                    .get("dek_id")
                    .ok_or(anyhow!("dek_id not in dek ddb record"))?
                    .as_s()
                    .map_err(|value| anyhow!("could not return {:?} as string", value))?;
                event!(Level::DEBUG, "DEK ID {} identified as being usable", dek_id);
                let dek_record = self
                    .ddb
                    .get_item()
                    .table_name(&self.dek_table_name)
                    .key("dek_id", AttributeValue::S(dek_id.to_string()))
                    .send()
                    .await?
                    .item
                    .ok_or("No wrapped key found")
                    .map_err(|e| anyhow!(e))?;
                event!(Level::DEBUG, "Fetched DEK ID {} from DDB", dek_id);
                self.ddb
                    .update_item()
                    .table_name(&self.dek_table_name)
                    .key("dek_id", AttributeValue::S(dek_id.to_string()))
                    .update_expression("SET usage_count = usage_count + :u, isAvailable = :a")
                    .expression_attribute_values(":u", AttributeValue::N(LEASE_SIZE.to_string()))
                    .expression_attribute_values(
                        ":a",
                        AttributeValue::N(
                            if dek_record
                                .get("usage_count")
                                .unwrap()
                                .as_n()
                                .unwrap()
                                .parse::<u32>()
                                .unwrap()
                                <= (MAX_DEK_USES - LEASE_SIZE)
                            {
                                "1".to_string()
                            } else {
                                "0".to_string()
                            },
                        ),
                    )
                    .send()
                    .await
                    .context("could not updated dek record in ddb")?;
                Ok(LeasedDek {
                    dek_id: dek_id.to_string(),
                    dek_ciphertext: safe_get_str(&dek_record, "dek_ciphertext")?,
                    remaining_uses: LEASE_SIZE,
                })
            }
        }
    }

    #[instrument(skip(self))]
    pub async fn get_availabile_dek_id(&self) -> anyhow::Result<String> {
        let mut current_dek = self.current_dek.lock().await;
        if current_dek.is_none() {
            event!(
                Level::DEBUG,
                "api server keystore doesn't have an available DEK. fetching one."
            );
            *current_dek = Some(self.get_or_create_dek().await?);
        }
        let dek = current_dek.as_mut().unwrap();
        let dek_id = dek.dek_id.clone();
        dek.remaining_uses -= 1;
        if dek.remaining_uses == 0 {
            event!(
                Level::DEBUG,
                "API-Keystore: current DEK lease is up. invalidating current DEK"
            );
            *current_dek = None;
        }
        Ok(dek_id)
    }
}
