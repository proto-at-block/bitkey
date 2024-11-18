use anyhow::Context;
use aws_sdk_dynamodb::client::Client as DdbClient;
use aws_sdk_dynamodb::types::AttributeValue;
use crypto::keys::PublicKey;
use serde::{Deserialize, Serialize};
use serde_dynamo::{from_item, to_item};
use tracing::instrument;
use wsm_common::bitcoin::Network;

/// Struct representing the customer's root key share. We use the data in this struct to derive subsequent
/// child BIP32 xprvs. Customers and `CustomerKeyShare` have a bijective relationship.
#[derive(Serialize, Deserialize, Debug)]
pub struct CustomerKeyShare {
    /// The partition key used to uniquely identify a `CustomerKeyShare`.
    pub root_key_id: String,
    /// Base64-encoded ciphertext of the customer's root key share details. Encrypted/decrypted using a
    /// data encryption key (DEK) whose `id` is also stored in this struct.
    pub share_details_ciphertext: String,
    /// Base64-encoded nonce used to encrypt/decrypt the customer's root key share details
    pub share_details_nonce: String,
    /// ID of the Data Encryption Key (DEK) used to decrypt/encrypt the customer's root key share
    pub dek_id: String,
    /// The aggregate public key
    pub aggregate_public_key: PublicKey,
    /// The bitcoin network type to be used with the customer's root key share
    #[serde(default)]
    pub network: Option<Network>,
}

impl CustomerKeyShare {
    pub fn new(
        root_key_id: String,
        share_details_ciphertext: String,
        share_details_nonce: String,
        dek_id: String,
        aggregate_public_key: PublicKey,
        network: Network,
    ) -> Self {
        Self {
            root_key_id,
            share_details_ciphertext,
            share_details_nonce,
            dek_id,
            aggregate_public_key,
            network: Some(network),
        }
    }
}

#[derive(Debug, Clone)]
pub struct CustomerKeyShareStore {
    cks_table_name: String,
    client: DdbClient,
}

impl CustomerKeyShareStore {
    pub fn new(client: DdbClient, customer_key_shares_table_name: &str) -> Self {
        CustomerKeyShareStore {
            cks_table_name: customer_key_shares_table_name.to_string(),
            client,
        }
    }

    #[instrument(skip(self))]
    pub async fn get_customer_key_share(
        &self,
        root_key_id: &str,
    ) -> anyhow::Result<Option<CustomerKeyShare>> {
        let item_output = self
            .client
            .get_item()
            .table_name(&self.cks_table_name)
            .key("root_key_id", AttributeValue::S(root_key_id.to_string()))
            .send()
            .await?
            .item;

        match item_output {
            Some(item) => {
                from_item(item).context("Unable to parse database object to CustomerKeyShare")
            }
            None => Ok(None),
        }
    }

    #[instrument(skip(self))]
    pub async fn put_customer_key_share(
        &self,
        customer_key: &CustomerKeyShare,
    ) -> anyhow::Result<()> {
        let customer_key_share_item = to_item(customer_key)?;

        self.client
            .put_item()
            .table_name(self.cks_table_name.clone())
            .set_item(Some(customer_key_share_item))
            .send()
            .await
            .context("could not write new customer key share to ddb")?;
        Ok(())
    }
}
