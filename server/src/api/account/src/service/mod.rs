use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use isocountry::CountryCode;
use repository::account::AccountRepository;
use repository::consent::ConsentRepository;
use types::account::bitcoin::Network;
use types::account::entities::{
    CommsVerificationClaim, CommsVerificationScope, DescriptorBackupsSet, FullAccount, Keyset,
    LiteAccount, TouchpointPlatform,
};
use types::account::identifiers::{AccountId, AuthKeysId, KeyDefinitionId, KeysetId, TouchpointId};
use types::account::keys::{FullAccountAuthKeys, LiteAccountAuthKeys, SoftwareAccountAuthKeys};
use types::account::spend_limit::SpendingLimit;
use types::account::spending::{SpendingDistributedKey, SpendingKeyset};
use userpool::userpool::UserPoolService;

mod activate_touchpoint_for_account;
mod add_push_touchpoint_to_account;
mod clear_push_touchpoints;
mod complete_onboarding;
mod create_account_and_keysets;
mod create_and_rotate_auth_keys;
mod create_inactive_spending_keyset;
mod create_lite_account;
mod create_software_account;
mod delete_account;
mod fetch_account;
mod fetch_and_update_spend_limit;
mod fetch_or_create_comms_verification_claim;
mod fetch_touchpoint;
mod migrations;
mod put_comms_verification_claim;
mod put_inactive_spending_distributed_key;
mod put_transaction_verification_policy;
mod rotate_to_spending_key_definition;
mod rotate_to_spending_keyset;
pub mod tests;
mod update_descriptor_backups;
mod upgrade_lite_account_to_full_account;

#[derive(Clone)]
pub struct Service {
    account_repo: AccountRepository,
    consent_repo: ConsentRepository,
    userpool_service: UserPoolService,
}

impl Service {
    pub fn new(
        account_repo: AccountRepository,
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
pub struct CreateSoftwareAccountInput<'a> {
    pub account_id: &'a AccountId,
    pub auth_key_id: AuthKeysId,
    pub auth: SoftwareAccountAuthKeys,
    pub is_test_account: bool,
}

#[derive(Debug, Clone)]
pub struct FetchKeysetsInput {
    pub account_id: String,
}

#[derive(Debug, Clone)]
pub struct AddPushTouchpointToAccountInput<'a> {
    pub account_id: &'a AccountId,
    pub use_local_sns: bool,
    pub platform: TouchpointPlatform,
    pub device_token: String,
    pub access_token: String,
}

#[derive(Debug, Clone)]
pub struct FetchOrCreatePhoneTouchpointInput<'a> {
    pub account_id: &'a AccountId,
    pub phone_number: String,
    pub country_code: CountryCode,
}

#[derive(Debug, Clone)]
pub struct FetchOrCreateEmailTouchpointInput<'a> {
    pub account_id: &'a AccountId,
    pub email_address: String,
}

#[derive(Debug, Clone)]
pub struct ActivateTouchpointForAccountInput<'a> {
    pub account_id: &'a AccountId,
    pub touchpoint_id: TouchpointId,
    pub dry_run: bool,
}

#[derive(Debug)]
pub struct FetchAndUpdateSpendingLimitInput<'a> {
    pub account_id: &'a AccountId,
    pub new_spending_limit: Option<SpendingLimit>,
}

#[derive(Debug, Clone)]
pub struct FetchOrCreateCommsVerificationClaimInput<'a> {
    pub account_id: &'a AccountId,
    pub scope: CommsVerificationScope,
}

#[derive(Debug, Clone)]
pub struct PutCommsVerificationClaimInput<'a> {
    pub account_id: &'a AccountId,
    pub claim: CommsVerificationClaim,
}

#[derive(Debug, Clone)]
pub struct FetchTouchpointByIdInput<'a> {
    pub account_id: &'a AccountId,
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

#[derive(Clone)]
pub struct PutInactiveSpendingDistributedKeyInput<'a> {
    pub account_id: &'a AccountId,
    pub spending_key_definition_id: &'a KeyDefinitionId,
    pub spending: SpendingDistributedKey,
}

#[derive(Debug, Clone)]
pub struct RotateToSpendingKeyDefinitionInput<'a> {
    pub account_id: &'a AccountId,
    pub key_definition_id: &'a KeyDefinitionId,
}

#[derive(Debug, Clone)]
pub struct UpdateDescriptorBackupsInput<'a> {
    pub account: &'a FullAccount,
    pub descriptor_backups_set: DescriptorBackupsSet,
}

fn descriptor_backup_exists_for_private_keyset(
    full_account: &FullAccount,
    keyset_id: &KeysetId,
) -> bool {
    let is_private = full_account
        .spending_keysets
        .get(keyset_id)
        .and_then(|ks| ks.optional_private_multi_sig())
        .is_some();

    let missing_backup = full_account
        .descriptor_backups_set
        .as_ref()
        .and_then(|b| b.get_sealed_descriptor(keyset_id))
        .is_none();

    !is_private || !missing_backup
}
