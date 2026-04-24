mod assemble_psbt;
mod attestation;
mod authentication;
mod coredump;
mod decompose_psbt;
mod device_id;
mod eek_restoration_unseal;
mod feature_flags;
mod fingerprint;
mod full_account_cloud_backup_restoration;
mod full_account_cloud_backup_restoration_continue;
mod fwup;
mod generate_keys;
mod get_address;
mod get_confirmation_result;
mod grants;
mod lost_app_recovery;
mod lost_app_recovery_continue;
mod lost_app_recovery_sign_challenge;
mod metadata;
mod provision_app_auth_key;
mod query_authentication;
mod recovery_authorize_lost_app;
mod recovery_authorize_lost_hw;
mod rotate_app_auth_keys;
mod seal_key;
mod show_confirmation_screen;
mod sign_action_proof;
mod sign_challenge_and_seal_seks;
mod sign_sighash;
mod sign_stream;
mod sign_stream_serializer;
mod sign_transaction;
mod sign_transaction_chunked;
mod sign_tx_request;
mod sweep_sign;
mod telemetry;
mod unseal_key;
mod upgrade_authorize_w3;
mod upgrade_rotate_app_auth_keys;
mod verify_keys_and_build_descriptor;
mod version;
mod wipe_state;

pub use crate::fwpb::fwup_finish_rsp::FwupFinishRspStatus;
pub use crate::fwpb::get_fingerprint_enrollment_status_rsp::FingerprintEnrollmentStatus;
pub use crate::fwpb::get_unlock_method_rsp::UnlockMethod;
pub use crate::fwpb::BtcDisplayUnit;
pub use crate::fwpb::BtcNetwork;
pub use assemble_psbt::assemble_psbt_signatures;
pub use attestation::{GetCert, SignVerifyAttestationChallenge};
pub use authentication::LockDevice;
pub use authentication::SignChallenge;
pub use authentication::AUTHENTICATION_DERIVATION_PATH;
pub use authentication::{GetAuthenticationKey, GetUnlockMethod, UnlockInfo};
pub use coredump::CoredumpFragment;
pub use coredump::GetCoredumpCount;
pub use coredump::GetCoredumpFragment;
pub use decompose_psbt::{decompose_psbt, DecomposedPsbt};
pub use device_id::DeviceIdentifiers;
pub use device_id::DeviceInfo;
pub use device_id::GetDeviceIdentifiers;
pub use device_id::GetDeviceInfo;
pub use device_id::GetTelemetryIdentifiers;
pub use device_id::SecureBootConfig;
pub use device_id::{BioMatchStats, TemplateMatchStats};
pub use device_id::{DeviceInfoMcu, McuInfo};
pub use eek_restoration_unseal::{EekRestorationUnseal, EekRestorationUnsealResult};
pub use feature_flags::FirmwareFeatureFlag;
pub use feature_flags::FirmwareFeatureFlagCfg;
pub use feature_flags::GetFirmwareFeatureFlags;
pub use feature_flags::SetFirmwareFeatureFlags;
pub use fingerprint::{
    CancelFingerprintEnrollment, DeleteFingerprint, EnrolledFingerprints, EnrollmentDiagnostics,
    FingerprintEnrollmentResult, GetEnrolledFingerprints, GetFingerprintEnrollmentStatus,
    SetFingerprintLabel, StartFingerprintEnrollment,
};
pub use full_account_cloud_backup_restoration::{
    FullAccountCloudBackupRestoration, FullAccountCloudBackupRestorationResult,
};
pub use full_account_cloud_backup_restoration_continue::{
    FullAccountCloudBackupRestorationContinue, FullAccountCloudBackupRestorationContinueResult,
};
pub use fwup::FwupFinish;
pub use fwup::FwupMode;
pub use fwup::FwupStart;
pub use fwup::FwupStartResult;
pub use fwup::FwupTransfer;
pub use generate_keys::find_next_bip84_derivation;
pub use generate_keys::GetInitialSpendingKey;
pub use generate_keys::GetNextSpendingKey;
pub use get_address::{GetAddress, GetAddressResult};
pub use get_confirmation_result::{ConfirmedCommandResult, GetConfirmationResult};
pub use grants::{FingerprintResetFinalize, FingerprintResetRequest};
pub use lost_app_recovery::{LostAppRecovery, LostAppRecoveryResult};
pub use lost_app_recovery_continue::{LostAppRecoveryContinue, LostAppRecoveryContinueResult};
pub use lost_app_recovery_sign_challenge::{
    LostAppRecoverySignChallenge, LostAppRecoverySignChallengeResult,
};
pub use metadata::FirmwareMetadata;
pub use metadata::FirmwareSlot;
pub use metadata::GetFirmwareMetadata;
pub use metadata::McuName;
pub use metadata::McuRole;
pub use provision_app_auth_key::ProvisionAppAuthKey;
pub use query_authentication::QueryAuthentication;
pub use recovery_authorize_lost_app::{RecoveryAuthorizeLostApp, RecoveryAuthorizeLostAppResult};
pub use recovery_authorize_lost_hw::{RecoveryAuthorizeLostHw, RecoveryAuthorizeLostHwResult};
pub use rotate_app_auth_keys::{RotateAppAuthKeys, RotateAppAuthKeysResult};
pub use seal_key::SealKey;
pub use show_confirmation_screen::ShowConfirmationScreen;
pub use sign_action_proof::{SignActionProof, SignActionProofResult};
pub use sign_challenge_and_seal_seks::{SignChallengeAndSealSeks, SignChallengeAndSealSeksResult};
pub use sign_sighash::SignedSighash;
pub use sign_stream::{
    GetTxSignature, GetTxSignaturesBatch, SignStreamFinalize, SignStreamFinalizeResult,
    SignStreamStart, SignStreamStartResult, SignStreamTransfer, SignStreamTransferResult,
    TxSignature,
};
pub use sign_stream_serializer::{
    chunk_payload, compute_commitment_hash, payload_size, serialize_stream_payload, CHUNK_SIZE,
};
pub use sign_transaction::SignTransaction;
pub use sign_transaction_chunked::{SignStart, SignStartResult, SignTransfer, SignTransferResult};
pub use sign_tx_request::{
    InputSignatureTuple, SignTxInputData, SignTxOutputData, SignTxRequest, SignTxRequestResult,
};
pub use sweep_sign::{
    SweepSignRequest, SweepSignStreamStart, SweepSignStreamStartResult, SweepXpub,
};
pub use telemetry::EventFragment;
pub use telemetry::GetEvents;
pub use unseal_key::UnsealKey;
pub use upgrade_authorize_w3::{UpgradeAuthorizeW3, UpgradeAuthorizeW3Result};
pub use upgrade_rotate_app_auth_keys::{UpgradeRotateAppAuthKeys, UpgradeRotateAppAuthKeysResult};
pub use verify_keys_and_build_descriptor::VerifyKeysAndBuildDescriptor;
pub use version::Version;
pub use wipe_state::{WipeState, WipeStateResult};

pub type SealedKey = Vec<u8>;
pub type UnsealedKey = [u8; 32];
pub type Signature = bitcoin::secp256k1::ecdsa::Signature;
pub use bitcoin::psbt::Psbt as PartiallySignedTransaction;
pub use miniscript::DescriptorPublicKey;
