mod attestation;
mod authentication;
mod coredump;
mod device_id;
mod feature_flags;
mod fingerprint;
mod fwup;
mod generate_keys;
mod grants;
mod metadata;
mod provision_app_auth_key;
mod query_authentication;
mod seal_key;
mod sign_sighash;
mod sign_transaction;
mod telemetry;
mod unseal_key;
mod version;
mod wipe_state;

pub use crate::fwpb::fwup_finish_rsp::FwupFinishRspStatus;
pub use crate::fwpb::get_fingerprint_enrollment_status_rsp::FingerprintEnrollmentStatus;
pub use crate::fwpb::get_unlock_method_rsp::UnlockMethod;
pub use crate::fwpb::BtcNetwork;
pub use attestation::{GetCert, SignVerifyAttestationChallenge};
pub use authentication::LockDevice;
pub use authentication::SignChallenge;
pub use authentication::SignChallengeV2;
pub use authentication::AUTHENTICATION_DERIVATION_PATH;
pub use authentication::{
    GetAuthenticationKey, GetAuthenticationKeyV2, GetUnlockMethod, UnlockInfo,
};
pub use coredump::CoredumpFragment;
pub use coredump::GetCoredumpCount;
pub use coredump::GetCoredumpFragment;
pub use device_id::DeviceIdentifiers;
pub use device_id::DeviceInfo;
pub use device_id::GetDeviceIdentifiers;
pub use device_id::GetDeviceInfo;
pub use device_id::GetTelemetryIdentifiers;
pub use device_id::SecureBootConfig;
pub use device_id::{BioMatchStats, TemplateMatchStats};
pub use feature_flags::FirmwareFeatureFlag;
pub use feature_flags::FirmwareFeatureFlagCfg;
pub use feature_flags::GetFirmwareFeatureFlags;
pub use feature_flags::SetFirmwareFeatureFlags;
pub use fingerprint::{
    CancelFingerprintEnrollment, DeleteFingerprint, EnrolledFingerprints, EnrollmentDiagnostics,
    FingerprintEnrollmentResult, GetEnrolledFingerprints, GetFingerprintEnrollmentStatus,
    SetFingerprintLabel, StartFingerprintEnrollment,
};
pub use fwup::FwupFinish;
pub use fwup::FwupMode;
pub use fwup::FwupStart;
pub use fwup::FwupTransfer;
pub use generate_keys::find_next_bip84_derivation;
pub use generate_keys::GetInitialSpendingKey;
pub use generate_keys::GetNextSpendingKey;
pub use grants::{FingerprintResetFinalize, FingerprintResetRequest};
pub use metadata::FirmwareMetadata;
pub use metadata::FirmwareSlot;
pub use metadata::GetFirmwareMetadata;
pub use provision_app_auth_key::ProvisionAppAuthKey;
pub use query_authentication::QueryAuthentication;
pub use seal_key::SealKey;
pub use sign_sighash::SignedSighash;
pub use sign_transaction::SignTransaction;
pub use telemetry::EventFragment;
pub use telemetry::GetEvents;
pub use unseal_key::UnsealKey;
pub use version::Version;
pub use wipe_state::WipeState;

pub type SealedKey = Vec<u8>;
pub type UnsealedKey = [u8; 32];
pub type Signature = bitcoin::secp256k1::ecdsa::Signature;
pub use bitcoin::psbt::PartiallySignedTransaction;
pub use miniscript::DescriptorPublicKey;
