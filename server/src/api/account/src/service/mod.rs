use crate::entities::{
    CommsVerificationClaim, CommsVerificationScope, FullAccountAuthKeys, LiteAccount,
    LiteAccountAuthKeys, SpendingKeyset, TouchpointPlatform,
};
use crate::spend_limit::SpendingLimit;
use crate::{
    entities::{Keyset, Network},
    repository::Repository,
};
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use isocountry::CountryCode;
use repository::consent::Repository as ConsentRepository;
use types::account::identifiers::{AccountId, AuthKeysId, KeysetId, TouchpointId};
use userpool::userpool::UserPoolService;

mod activate_touchpoint_for_account;
mod add_push_touchpoint_to_account;
mod clear_push_touchpoints;
mod complete_onboarding;
mod create_account_and_keysets;
mod create_and_rotate_auth_keys;
mod create_inactive_spending_keyset;
mod create_lite_account;
mod delete_account;
mod fetch_account;
mod fetch_and_update_spend_limit;
mod fetch_or_create_comms_verification_claim;
mod fetch_touchpoint;
mod migrations;
mod put_comms_verification_claim;
mod rotate_to_spending_keyset;
mod upgrade_lite_account_to_full_account;

#[derive(Clone)]
pub struct Service {
    account_repo: Repository,
    consent_repo: ConsentRepository,
    userpool_service: UserPoolService,
}

impl Service {
    pub fn new(
        account_repo: Repository,
        consent_repo: ConsentRepository,
        userpool_service: UserPoolService,
    ) -> Self {
        Self {
            account_repo,
            consent_repo,
            userpool_service,
        }
    }
}

#[derive(Debug, Clone)]
pub struct FetchAccountInput<'a> {
    pub account_id: &'a AccountId,
}

#[derive(Debug, Clone)]
pub struct FetchAccountByAuthKeyInput {
    pub pubkey: PublicKey,
}

#[derive(Clone)]
pub struct CreateInactiveSpendingKeysetInput {
    pub account_id: AccountId,
    pub spending_keyset_id: KeysetId,
    pub spending: SpendingKeyset,
}

#[derive(Clone)]
pub struct CreateAccountAndKeysetsInput {
    pub account_id: AccountId,
    pub network: Network,
    pub keyset_id: KeysetId,
    pub auth_key_id: AuthKeysId,
    // TODO [BKR-518]: Clean up keysets
    pub keyset: Keyset,
    pub is_test_account: bool,
}

#[derive(Debug, Clone)]
pub struct CreateAndRotateAuthKeysInput<'a> {
    pub account_id: &'a AccountId,
    pub app_auth_pubkey: PublicKey,
    pub hardware_auth_pubkey: PublicKey,
    // This is optional as older clients won't have a recovery auth pubkey
    pub recovery_auth_pubkey: Option<PublicKey>,
}

#[derive(Debug, Clone)]
pub struct CreateLiteAccountInput<'a> {
    pub account_id: &'a AccountId,
    pub auth_key_id: AuthKeysId,
    pub auth: LiteAccountAuthKeys,
    pub is_test_account: bool,
}

#[derive(Debug, Clone)]
pub struct FetchKeysetsInput {
    pub account_id: String,
}

#[derive(Debug, Clone)]
pub struct AddPushTouchpointToAccountInput {
    pub account_id: AccountId,
    pub use_local_sns: bool,
    pub platform: TouchpointPlatform,
    pub device_token: String,
    pub access_token: String,
}

#[derive(Debug, Clone)]
pub struct FetchOrCreatePhoneTouchpointInput {
    pub account_id: AccountId,
    pub phone_number: String,
    pub country_code: CountryCode,
}

#[derive(Debug, Clone)]
pub struct FetchOrCreateEmailTouchpointInput {
    pub account_id: AccountId,
    pub email_address: String,
}

#[derive(Debug, Clone)]
pub struct ActivateTouchpointForAccountInput {
    pub account_id: AccountId,
    pub touchpoint_id: TouchpointId,
}

#[derive(Debug)]
pub struct FetchAndUpdateSpendingLimitInput<'a> {
    pub account_id: &'a AccountId,
    pub new_spending_limit: Option<SpendingLimit>,
}

#[derive(Debug, Clone)]
pub struct FetchOrCreateCommsVerificationClaimInput {
    pub account_id: AccountId,
    pub scope: CommsVerificationScope,
}

#[derive(Debug, Clone)]
pub struct PutCommsVerificationClaimInput {
    pub account_id: AccountId,
    pub claim: CommsVerificationClaim,
}

#[derive(Debug, Clone)]
pub struct FetchTouchpointByIdInput {
    pub account_id: AccountId,
    pub touchpoint_id: TouchpointId,
}

#[derive(Debug, Clone)]
pub struct RotateToSpendingKeysetInput<'a> {
    pub account_id: &'a AccountId,
    pub keyset_id: &'a KeysetId,
}

#[derive(Debug, Clone)]
pub struct CompleteOnboardingInput<'a> {
    pub account_id: &'a AccountId,
}

#[derive(Debug, Clone)]
pub struct ClearPushTouchpointsInput<'a> {
    pub account_id: &'a AccountId,
}

#[derive(Clone)]
pub struct UpgradeLiteAccountToFullAccountInput<'a> {
    pub lite_account: &'a LiteAccount,
    pub keyset_id: KeysetId,
    pub spending_keyset: SpendingKeyset,
    pub auth_key_id: AuthKeysId,
    pub auth_keys: FullAccountAuthKeys,
}

#[derive(Debug, Clone)]
pub struct DeleteAccountInput<'a> {
    pub account_id: &'a AccountId,
}
