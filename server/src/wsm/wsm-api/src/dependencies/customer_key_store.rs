use anyhow::Context;
use aws_sdk_dynamodb::client::Client as DdbClient;
use aws_sdk_dynamodb::types::AttributeValue;
use serde::{Deserialize, Serialize};
use serde_dynamo::{from_item, to_item};
use tracing::instrument;
use wsm_common::bitcoin::Network;
use wsm_common::messages::DomainFactoredXpub;

/// Struct representing the customer's root key. We use the data in this struct to derive subsequent
/// child BIP32 xprvs. Customers and `CustomerKey` have a bijective relationship.
#[derive(Serialize, Deserialize, Debug)]
pub struct CustomerKey {
    /// The partition key used to uniquely identify a `CustomerKey`.
    pub root_key_id: String,
    /// Base64-encoded ciphertext of the customer's root key. Encrypted/decrypted using a
    /// data encryption key (DEK) whose `id` is also stored in this struct.
    pub key_ciphertext: String,
    /// Base64-encoded nonce used to encrypt/decrypt the customer's root key
    pub key_nonce: String,
    /// Xpub wrapped in a `DescriptorKey` format -- it includes information about the xpub itself,
    /// as well as information about its origin like derivation path.
    pub xpub_descriptor: String,
    /// ID of the Data Encryption Key (DEK) used to decrypt/encrypt the customer's root key
    pub dek_id: String,
    /// A list of cached Xpubs with their intended domain.
    pub xpubs: Vec<DomainFactoredXpub>,
    /// The bitcoin network type to be used with the customer's root key
    #[serde(default)]
    pub network: Option<Network>,
}

impl CustomerKey {
    pub fn new(
        root_key_id: String,
        key_ciphertext: String,
        key_nonce: String,
        xpub_descriptor: String,
        dek_id: String,
        xpubs: Vec<DomainFactoredXpub>,
        network: Network,
    ) -> Self {
        Self {
            root_key_id,
            key_ciphertext,
            key_nonce,
            xpub_descriptor,
            dek_id,
            xpubs,
            network: Some(network),
        }
    }
}

#[derive(Debug, Clone)]
pub struct CustomerKeyStore {
    ck_table_name: String,
    client: DdbClient,
}

impl CustomerKeyStore {
    pub fn new(client: DdbClient, customer_keys_table_name: &str) -> Self {
        CustomerKeyStore {
            ck_table_name: customer_keys_table_name.to_string(),
            client,
        }
    }

    #[instrument(skip(self))]
    pub async fn get_customer_key(&self, root_key_id: &str) -> anyhow::Result<Option<CustomerKey>> {
        let item_output = self
            .client
            .get_item()
            .table_name(&self.ck_table_name)
            .key("root_key_id", AttributeValue::S(root_key_id.to_string()))
            .send()
            .await?
            .item;

        match item_output {
            Some(item) => from_item(item).context("Unable to parse database object to CustomerKey"),
            None => Ok(None),
        }
    }

    #[instrument(skip(self))]
    pub async fn put_customer_key(&self, customer_key: &CustomerKey) -> anyhow::Result<()> {
        let customer_key_item = to_item(customer_key)?;

        self.client
            .put_item()
            .table_name(self.ck_table_name.clone())
            .set_item(Some(customer_key_item))
            .send()
            .await
            .context("could not write new customer key to ddb")?;
        Ok(())
    }
}
