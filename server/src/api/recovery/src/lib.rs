use crate::repository::RecoveryRepository;
use ::repository::public_key::PublicKeyRepository;
use account::service::{FetchAccountByAuthKeyInput, Service as AccountService};
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use entities::RecoveryStatus;
use error::RecoveryError;
use types::account::identifiers::AccountId;

pub mod entities;
pub mod error;
pub(crate) mod helpers;
pub mod metrics;
pub mod repository;
pub mod routes;
pub mod service;
pub mod state_machine;

pub(crate) async fn ensure_pubkeys_unique(
    account_service: &AccountService,
    recovery_service: &RecoveryRepository,
    public_key_repo: &PublicKeyRepository,
    account_id: &AccountId,
    app_auth_pubkey: Option<PublicKey>,
    hw_auth_pubkey: Option<PublicKey>,
    recovery_auth_pubkey: Option<PublicKey>,
) -> Result<(), RecoveryError> {
    // TODO: We should extract this and make reusable between recovery & onboarding

    if let Some(app_auth_pubkey) = app_auth_pubkey {
        // If there's an existing account with the same app auth pubkey, error
        if account_service
            .fetch_account_by_app_pubkey(FetchAccountByAuthKeyInput {
                pubkey: app_auth_pubkey,
            })
            .await?
            .is_some()
        {
            return Err(RecoveryError::AppAuthPubkeyReuseAccount);
        }

        // If there's an existing pending recovery with the same destination app auth pubkey, error
        if recovery_service
            .fetch_optional_recovery_by_app_auth_pubkey(app_auth_pubkey, RecoveryStatus::Pending)
            .await?
            .is_some()
        {
            return Err(RecoveryError::AppAuthPubkeyReuseRecovery);
        }
    }

    if let Some(recovery_auth_pubkey) = recovery_auth_pubkey {
        // If there's an existing account with the same recovery auth pubkey, error
        if account_service
            .fetch_account_by_recovery_pubkey(FetchAccountByAuthKeyInput {
                pubkey: recovery_auth_pubkey,
            })
            .await?
            .is_some()
        {
            return Err(RecoveryError::RecoveryAuthPubkeyReuseAccount);
        }

        // If there's an existing pending recovery with the same destination recovery auth pubkey, error
        if recovery_service
            .fetch_optional_recovery_by_recovery_auth_pubkey(
                recovery_auth_pubkey,
                RecoveryStatus::Pending,
            )
            .await?
            .is_some()
        {
            return Err(RecoveryError::RecoveryAuthPubkeyReuseRecovery);
        }
    }

    if let Some(hw_auth_pubkey) = hw_auth_pubkey {
        // Check if the hw key was ever previously active on a different account
        if let Some(existing_record) = public_key_repo
            .fetch_by_public_key(&hw_auth_pubkey.to_string())
            .await?
        {
            if existing_record.account_id != *account_id {
                return Err(RecoveryError::HwAuthPubkeyReuseAccount);
            }
        }

        // If there's an existing pending recovery with the same destination hw auth pubkey, error
        if recovery_service
            .fetch_optional_recovery_by_hardware_auth_pubkey(
                hw_auth_pubkey,
                RecoveryStatus::Pending,
            )
            .await?
            .is_some()
        {
            return Err(RecoveryError::HwAuthPubkeyReuseRecovery);
        }
    }

    Ok(())
}
