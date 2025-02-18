use anyhow::Context;
use aws_sdk_dynamodb::client::Client as DdbClient;
use aws_sdk_dynamodb::types::AttributeValue;
use crypto::keys::PublicKey;
use serde::{Deserialize, Serialize};
use serde_dynamo::{from_item, to_item};
use time::{format_description::well_known::Rfc3339, serde::rfc3339, OffsetDateTime};
use tracing::instrument;
use wsm_common::bitcoin::Network;

/// Struct representing the customer's root key share. We use the data in this struct to derive subsequent
/// child BIP32 xprvs. Customers and `CustomerKeyShare` have a bijective relationship.
#[derive(Serialize, Deserialize, Clone, Debug)]
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
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub network: Option<Network>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pending_share_details_ciphertext: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pending_share_details_nonce: Option<String>,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
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
            pending_share_details_ciphertext: None,
            pending_share_details_nonce: None,
            created_at: OffsetDateTime::now_utc(),
            updated_at: OffsetDateTime::now_utc(),
        }
    }

    pub fn with_updated_at(&self, updated_at: OffsetDateTime) -> Self {
        Self {
            updated_at,
            ..self.to_owned()
        }
    }

    pub fn with_pending_share_details(
        &self,
        pending_share_details_ciphertext: String,
        pending_share_details_nonce: String,
    ) -> Self {
        Self {
            pending_share_details_ciphertext: Some(pending_share_details_ciphertext),
            pending_share_details_nonce: Some(pending_share_details_nonce),
            ..self.to_owned()
        }
    }

    pub fn with_share_details(
        &self,
        share_details_ciphertext: String,
        share_details_nonce: String,
    ) -> Self {
        Self {
            share_details_ciphertext,
            share_details_nonce,
            pending_share_details_ciphertext: None,
            pending_share_details_nonce: None,
            ..self.to_owned()
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
        customer_key_share: &CustomerKeyShare,
    ) -> anyhow::Result<()> {
        let customer_key_share_item =
            to_item(customer_key_share.with_updated_at(OffsetDateTime::now_utc()))?;

        let formatted_updated_at = customer_key_share
            .updated_at
            .format(&Rfc3339)
            .context("failed to format updated_at")?;

        self.client
            .put_item()
            .table_name(self.cks_table_name.clone())
            .set_item(Some(customer_key_share_item))
            .condition_expression("attribute_not_exists(updated_at) OR updated_at = :updated_at")
            .expression_attribute_values(":updated_at", AttributeValue::S(formatted_updated_at))
            .send()
            .await
            .context("could not write customer key share to ddb")?;
        Ok(())
    }
}
