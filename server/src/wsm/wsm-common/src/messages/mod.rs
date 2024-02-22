pub mod api;
pub mod enclave;

use std::fmt::{Debug, Formatter};

use crate::derivation::WSMSupportedDomain;
use const_format::concatcp;
use serde::{Deserialize, Serialize};

pub const TEST_DEK_ID: &str = "THIS_IS_A_FAKE_DEK_ID_WHAT_DO_THEY_LOOK_LIKE_THOUGH_HUH_I_DONNO";
pub const TEST_CMK_ID: &str = "THIS_IS_A_FAKE_KMS_CMK_ID";
pub const TEST_KEY_ID: &str = "urn:wallet-keyset:00000000000000000000000000";
pub const TEST_KEY_IDS: [&str; 5] = [
    "00000000000000000000000000",
    "00000000-0000-0000-0000-000000000000",
    "urn:wallet:account:00000000000000000000000000:00000000000000000000000000",
    "urn:wallet-account:00000000000000000000000000",
    "urn:wallet-keyset:00000000000000000000000000",
];
pub const TEST_XPUB: &str = "tpubD6NzVbkrYhZ4YPNfxZxodhEgkY1fbM9oc7pFb8q6FzQhf61LZPyiH5gywAcrieNLZUmLNG7P6EmjbR43V1SiFRw5mUg4LdXHAYCGVPo8Dmh";
pub const TEST_XPUB_SPEND_ORIGIN: &str = "[c345e1e9/84'/1'/0']";
pub const TEST_XPUB_SPEND: &str = "tpubDDC5YGNGhebUAGw8nKsTCTbfutQwAXNzyATcnCsbhCjfdt2a8cpGbojfgAzPnsdsXxVypwjz2uGUV9dpWh211PeYhuHHumjRs7dgRLKcKk1";
pub const TEST_DPUB_SPEND: &str = concatcp!(TEST_XPUB_SPEND_ORIGIN, TEST_XPUB_SPEND, "/*");
pub const TEST_XPUB_CONFIG_ORIGIN: &str = "[c345e1e9/212152'/0'/0']";
pub const TEST_XPUB_CONFIG: &str = "tpubDCxnJZFqzhFis9Ytx1y3BCAvFmXcsprsVJGxd4V8134A38zAY3qssgE1ZtnUbT9XdmY9KtPvs33HtnyGBj1uf5hVKXDT3S26pvzWwdyWiHi";

#[derive(Serialize, Deserialize)]
pub struct SecretRequest<'a, T> {
    pub dek_id: String,
    pub endpoint: &'a str,
    pub data: T,
}

impl<'a, T> SecretRequest<'a, T> {
    pub fn new(endpoint: &'a str, dek_id: String, data: T) -> Self {
        Self {
            dek_id,
            endpoint,
            data,
        }
    }
}

impl<'a, T> Debug for SecretRequest<'a, T> {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("SecretRequest")
            .field("dek_id", &self.dek_id)
            .field("endpoint", &self.endpoint)
            .field("data", &"REDACTED")
            .finish()
    }
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct DomainFactoredXpub {
    pub domain: WSMSupportedDomain,
    pub xpub: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct SignedBlob {
    pub signature: String,
    pub root_key_id: String,
}
