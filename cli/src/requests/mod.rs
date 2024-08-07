pub mod display;
pub mod helper;
pub mod middleware;

use anyhow::Result;
use bdk::bitcoin::psbt::PartiallySignedTransaction;
use bdk::bitcoin::secp256k1::ecdsa::Signature;
use bdk::bitcoin::secp256k1::PublicKey;
use bdk::bitcoin::Network;
use bdk::descriptor::DescriptorPublicKey;
use bdk::miniscript::serde::{Deserialize, Serialize};
use rustify_derive::Endpoint;
use time::serde::rfc3339;
use time::{OffsetDateTime, UtcOffset};

use crate::nfc::SafeTransactor;
use crate::serde_helpers::{fromagerie_network, string as serde_string, AccountId, KeysetId};
use crate::signers::Authentication;

#[derive(Debug, Deserialize, Serialize)]
pub struct AuthKeypairRequest {
    // TODO: [W-774] Update visibility of struct after migration
    pub app: PublicKey,
    pub hardware: PublicKey,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct SoftwareOnlyAuthKeypairRequest {
    pub app: PublicKey,
    pub recovery: PublicKey,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct ConfigKeypairRequest {
    // TODO: [W-774] Update visibility of struct after migration
    pub hardware: PublicKey,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct SpendingKeysetRequest {
    // TODO: [W-774] Update visibility of struct after migration
    pub network: Network,
    #[serde(with = "serde_string")]
    pub app: DescriptorPublicKey,
    #[serde(with = "serde_string")]
    pub hardware: DescriptorPublicKey,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "/api/accounts",
    method = "POST",
    response = "CreateAccountResponse"
)]
pub struct CreateAccount {
    pub auth: AuthKeypairRequest, // TODO: [W-774] Update visibility of struct after migration
    pub spending: SpendingKeysetRequest, // TODO: [W-774] Update visibility of struct after migration
    pub is_test_account: bool,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "/api/accounts",
    method = "POST",
    response = "CreateSoftwareAccountResponse"
)]
pub struct CreateSoftwareAccount {
    pub auth: SoftwareOnlyAuthKeypairRequest, // TODO: [W-774] Update visibility of struct after migration
    pub is_test_account: bool,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "/api/hw-auth",
    method = "POST",
    response = "HardwareAuthenticationResponse"
)]
pub struct HardwareAuthenticationRequest {
    pub hw_auth_pubkey: PublicKey,
}

#[derive(Debug, Deserialize)]
pub struct HardwareAuthenticationResponse {
    pub account_id: AccountId,
    pub challenge: String,
    pub session: String,
}

#[derive(Deserialize, Debug)]
pub struct CreateAccountResponse {
    pub account_id: AccountId,
    #[serde(flatten)]
    pub keyset: CreateKeysetResponse,
}

#[derive(Deserialize, Debug)]
pub struct CreateSoftwareAccountResponse {
    pub account_id: AccountId,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}/sign-transaction",
    method = "POST",
    response = "SignTransactionResponse"
)]
pub struct SignTransactionRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
    #[serde(with = "serde_string")]
    pub psbt: PartiallySignedTransaction,
    pub settings: Settings,
}

#[derive(Debug, Default, Serialize)]
pub struct Settings {
    pub limit: SpendingLimit,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct SpendingLimit {
    pub amount: Money,
    pub time_zone_offset: UtcOffset,
}

impl Default for SpendingLimit {
    fn default() -> Self {
        Self {
            amount: Money::default(),
            time_zone_offset: UtcOffset::UTC,
        }
    }
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct Money {
    pub amount: u64,
    pub currency_code: CurrencyCode,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
// ISO 4217 currency codes
pub enum CurrencyCode {
    #[default]
    AUD = 36,
    CAD = 124,
    JPY = 392,
    KWD = 414,
    GBP = 826,
    USD = 840,
    EUR = 978,
    BTC = 1001, // Not an ISO code!
}

#[derive(Deserialize, Debug)]
pub struct SignTransactionResponse {
    #[serde(with = "serde_string")]
    pub tx: PartiallySignedTransaction,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}/keysets",
    method = "POST",
    response = "CreateKeysetResponse"
)]
pub struct CreateKeysetRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
    pub spending: SpendingKeysetRequest,
}

#[derive(Debug, Deserialize, Serialize)]
pub struct CreateKeysetResponse {
    pub keyset_id: KeysetId,
    #[serde(with = "serde_string")]
    pub spending: DescriptorPublicKey,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}/keysets",
    method = "GET",
    response = "KeysetsResponse"
)]
pub struct KeysetsRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
}

#[derive(Debug, Deserialize)]
pub struct KeysetsResponse {
    pub keysets: Vec<KeysetResponse>,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}/keysets/{self.keyset_id}",
    method = "PUT",
    response = "SetActiveKeysetResponse"
)]
pub struct SetActiveKeysetRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
    #[endpoint(skip)]
    pub keyset_id: KeysetId,
    pub dummy: bool,
}

#[derive(Debug, Deserialize)]
pub struct SetActiveKeysetResponse {}

#[derive(Debug, Deserialize)]
pub struct KeysetResponse {
    pub keyset_id: KeysetId,
    pub network: Network, // TODO: raise ticket about different Network serializations
    #[serde(with = "serde_string")]
    pub app_dpub: DescriptorPublicKey,
    #[serde(with = "serde_string")]
    pub hardware_dpub: DescriptorPublicKey,
    #[serde(with = "serde_string")]
    pub server_dpub: DescriptorPublicKey,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}/delay-notify",
    method = "POST",
    response = "RecoveryStatusResponse"
)]
pub struct CreateAccountDelayNotifyRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,

    pub delay_period_num_sec: Option<i64>,

    #[serde(with = "serde_string")]
    pub lost_factor: Factor,
    pub auth: AuthKeypairRequest,
    pub verification_code: Option<String>,
}

#[derive(Debug, Deserialize, Serialize)]
pub enum Factor {
    App,
    Hw,
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(tag = "type")]
enum RecoveryDestination {
    Internal { keyset_id: KeysetId },
    External,
}

#[derive(Deserialize, Debug)]
pub struct CreateAccountDelayNotifyResponse {
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}/delay-notify/sign-transaction",
    method = "POST",
    response = "SignDelayNotifyResponse"
)]
pub struct SignDelayNotifyWalletTransactionRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
    #[serde(with = "serde_string")]
    pub psbt: PartiallySignedTransaction,
}

#[derive(Deserialize, Debug)]
pub struct SignDelayNotifyResponse {
    #[serde(with = "serde_string")]
    pub tx: PartiallySignedTransaction,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}",
    method = "GET",
    response = "GetWalletStatusResponse"
)]
pub struct GetWalletStatusRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
}

#[derive(Deserialize, Debug)]
pub struct GetWalletStatusResponse {
    #[serde(flatten)]
    pub active_keyset: KeysetForWalletStatusResponse,
    pub recovery_status: PendingRecoveryForWalletStatusResponse,
}

#[derive(Debug, Deserialize)]
pub struct KeysetForWalletStatusResponse {
    pub keyset_id: KeysetId,
    pub spending: SpendingKeyResponse,
}

#[derive(Debug, Deserialize)]
pub struct SpendingKeyResponse {
    #[serde(with = "fromagerie_network")]
    pub network: Network,
    #[serde(with = "serde_string")]
    pub app_dpub: DescriptorPublicKey,
    #[serde(with = "serde_string")]
    pub hardware_dpub: DescriptorPublicKey,
    #[serde(with = "serde_string")]
    pub server_dpub: DescriptorPublicKey,
}

#[derive(Deserialize, Debug)]
pub struct PendingRecoveryForWalletStatusResponse {
    pub in_recovery: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub recovery_type: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub delay_and_notify_recovery: Option<DelayAndNotifyRecoveryStatus>,
}

#[derive(Deserialize, Debug)]
pub struct DelayAndNotifyRecoveryStatus {
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}/recovery",
    method = "GET",
    response = "RecoveryStatusResponse"
)]
pub struct RecoveryStatusRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
}

#[derive(Deserialize, Debug)]
pub struct RecoveryStatusResponse {
    pub pending_delay_notify: Option<PendingDelayNotify>,
}

#[derive(Deserialize, Debug)]
pub struct PendingDelayNotify {
    pub auth_keys: AuthKeypairRequest,
    #[serde(with = "rfc3339")]
    pub delay_start_time: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
    pub lost_factor: Factor,
}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}/delay-notify",
    method = "DELETE",
    response = "CancelDelayNotifyResponse"
)]
pub struct CancelDelayNotifyRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
    pub signature: Option<Signature>,
    pub check_signature: bool,
    pub verification_code: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct CancelDelayNotifyResponse {}

#[derive(Debug, Endpoint, Serialize)]
#[endpoint(
    path = "api/accounts/{self.account_id}/delay-notify/complete",
    method = "POST",
    response = "CompleteDelayNotifyResponse"
)]
pub struct CompleteDelayNotifyRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
    pub challenge: String,
    pub app_signature: String,
    pub hardware_signature: String,
}

impl CompleteDelayNotifyRequest {
    pub(crate) fn new(
        account_id: AccountId,
        application: &impl Authentication,
        hardware: &impl Authentication,
        context: &SafeTransactor,
    ) -> Result<Self> {
        let challenge: String = format!(
            "CompleteDelayNotify{hardware}{application}",
            hardware = hardware.public_key(),
            application = application.public_key()
        );
        let message = challenge.as_bytes();
        let app_signature = application.sign(message, context)?.to_string();
        let hardware_signature = hardware.sign(message, context)?.to_string();

        Ok(Self {
            account_id,
            challenge,
            app_signature,
            hardware_signature,
        })
    }
}

#[derive(Debug, Deserialize)]
pub struct CompleteDelayNotifyResponse {}

#[derive(Clone, Debug, Endpoint, Serialize)]
#[endpoint(
    path = "/api/accounts/{self.account_id}/mobile-pay",
    method = "PUT",
    response = "MobilePaySetupResponse"
)]
pub struct MobilePaySetupRequest {
    #[endpoint(skip)]
    pub account_id: AccountId,
    pub limit: SpendingLimit,
}

#[derive(Deserialize, Debug)]
pub struct MobilePaySetupResponse {}

#[derive(Clone, Debug, Endpoint, Serialize)]
#[endpoint(
    path = "/api/authenticate",
    method = "POST",
    response = "InitiateAuthResponse"
)]
pub struct InitiateAuthRequest {
    pub auth_request_key: AuthRequestKey,
}

#[derive(Clone, Serialize, Deserialize, Debug)]
pub enum AuthRequestKey {
    HwPubkey(PublicKey),
    AppPubkey(PublicKey),
    RecoveryPubkey(PublicKey),
}

#[derive(Deserialize, Debug)]
pub struct InitiateAuthResponse {
    pub username: String,
    pub account_id: AccountId,
    pub challenge: String,
    pub session: String,
}

#[derive(Clone, Debug, Endpoint, Serialize)]
#[endpoint(
    path = "/api/authenticate/tokens",
    method = "POST",
    response = "GetTokensResponse"
)]
pub struct GetTokensRequest {
    pub challenge: Option<ChallengeResponseParameters>,
    pub refresh_token: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
pub struct ChallengeResponseParameters {
    pub username: String,
    pub challenge_response: String,
    pub session: String,
}

#[derive(Deserialize, Debug)]
pub struct GetTokensResponse {
    pub access_token: String,
    pub refresh_token: String,
}
