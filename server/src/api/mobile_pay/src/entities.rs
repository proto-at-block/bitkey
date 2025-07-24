use serde::{Deserialize, Serialize};
use types::account::{entities::TransactionVerificationPolicy, spend_limit::SpendingLimit};
use utoipa::ToSchema;

#[derive(Debug, Default)]
pub struct Features {
    pub settings: Settings,
    pub daily_limit_sats: u64,
}

#[derive(Clone, Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
#[derive(Default)]
pub struct Settings {
    pub limit: SpendingLimit,
}

#[derive(Debug, Default)]
pub struct TransactionVerificationFeatures {
    pub policy: TransactionVerificationPolicy,
    pub verification_sats: u64,
}
