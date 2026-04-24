mod csek;
mod types;

use crate::csek::{SealKey, UnsealKey};
use bitcoin::{bip32::Fingerprint, secp256k1::PublicKey};
use teltra::{TelemetryIdentifiers, Teltra, TeltraError};
use wca::attestation::{Attestation, AttestationError};
use wca::command_interface::{Command, State};
use wca::commands::{assemble_psbt_signatures, decompose_psbt};
use wca::commands::{
    compute_commitment_hash, serialize_stream_payload, BioMatchStats, BtcDisplayUnit, BtcNetwork,
    CancelFingerprintEnrollment, ConfirmedCommandResult, CoredumpFragment,
    DecomposedPsbt, DeleteFingerprint, DescriptorPublicKey, DeviceIdentifiers, DeviceInfo,
    DeviceInfoMcu, EekRestorationUnseal, EekRestorationUnsealResult, EnrolledFingerprints,
    EnrollmentDiagnostics, EventFragment, FingerprintEnrollmentResult, FingerprintEnrollmentStatus,
    FingerprintResetFinalize, FingerprintResetRequest, FirmwareFeatureFlag, FirmwareFeatureFlagCfg,
    FirmwareMetadata, FirmwareSlot, FullAccountCloudBackupRestoration,
    FullAccountCloudBackupRestorationContinue, FullAccountCloudBackupRestorationContinueResult,
    FullAccountCloudBackupRestorationResult, FwupFinish, FwupFinishRspStatus, FwupMode, FwupStart,
    FwupStartResult, FwupTransfer, GetAddress, GetAddressResult, GetAuthenticationKey,
    GetCert, GetConfirmationResult,
    GetCoredumpCount, GetCoredumpFragment, GetDeviceIdentifiers, GetDeviceInfo,
    GetEnrolledFingerprints, GetEvents, GetFingerprintEnrollmentStatus, GetFirmwareFeatureFlags,
    GetFirmwareMetadata, GetInitialSpendingKey, GetNextSpendingKey, GetTelemetryIdentifiers,
    GetTxSignature, GetTxSignaturesBatch, GetUnlockMethod, InputSignatureTuple, LockDevice,
    LostAppRecovery, LostAppRecoveryContinue, LostAppRecoveryContinueResult, LostAppRecoveryResult,
    LostAppRecoverySignChallenge, LostAppRecoverySignChallengeResult, McuInfo, McuName, McuRole,
    PartiallySignedTransaction, ProvisionAppAuthKey, QueryAuthentication, RecoveryAuthorizeLostApp,
    RecoveryAuthorizeLostAppResult, RecoveryAuthorizeLostHw, RecoveryAuthorizeLostHwResult,
    RotateAppAuthKeys, RotateAppAuthKeysResult, SecureBootConfig, SetFingerprintLabel,
    UpgradeRotateAppAuthKeys, UpgradeRotateAppAuthKeysResult,
    SetFirmwareFeatureFlags, ShowConfirmationScreen, SignActionProof, SignActionProofResult,
    SignChallenge, SignChallengeAndSealSeks, SignChallengeAndSealSeksResult,
    SignStart, SignStartResult, SignStreamFinalize, SignStreamFinalizeResult, SignStreamStart,
    SignStreamStartResult, SignStreamTransfer, SignStreamTransferResult, SignTransaction,
    SignTransfer, SignTransferResult, SignTxInputData, SignTxOutputData, SignTxRequest,
    SignTxRequestResult, SignVerifyAttestationChallenge, Signature, StartFingerprintEnrollment,
    SweepSignRequest, SweepSignStreamStart, SweepSignStreamStartResult, SweepXpub,
    TemplateMatchStats, TxSignature, UnlockInfo, UpgradeAuthorizeW3, UpgradeAuthorizeW3Result,
    VerifyKeysAndBuildDescriptor, Version, WipeState, WipeStateResult,
};
use wca::errors::CommandError;
use wca::fwpb::cert_get_cmd::CertType;
use wca::fwpb::get_unlock_method_rsp::UnlockMethod;
use wca::fwpb::FingerprintHandle;
use wca::log_buffer::{
    disable_proto_exchange_logging, enable_proto_exchange_logging, get_proto_exchange_logs,
};
use wca::{EllipticCurve, KeyEncoding, PublicKeyHandle, PublicKeyMetadata, SignatureContext};

type BooleanState = State<bool>;
type U16State = State<u16>;
type PartiallySignedTransactionState = State<PartiallySignedTransaction>;
type FingerprintEnrollmentResultState = State<FingerprintEnrollmentResult>;
type FwupFinishRspStatusState = State<FwupFinishRspStatus>;
type BytesState = State<Vec<u8>>;
type FirmwareMetadataState = State<FirmwareMetadata>;
type DeviceIdentifiersState = State<DeviceIdentifiers>;
type FirmwareFeatureFlagsState = State<Vec<FirmwareFeatureFlagCfg>>;
type EventFragmentState = State<EventFragment>;
type TelemetryIdentifiersState = State<TelemetryIdentifiers>;
type DeviceInfoState = State<DeviceInfo>;
type DescriptorPublicKeyState = State<DescriptorPublicKey>;
type SignatureState = State<Signature>;
type CoredumpFragmentState = State<CoredumpFragment>;
type PublicKeyState = State<PublicKey>;

type EnrolledFingerprintsState = State<EnrolledFingerprints>;
type UnlockInfoState = State<UnlockInfo>;
type ConfirmedCommandResultState = State<ConfirmedCommandResult>;
type FwupStartResultState = State<FwupStartResult>;
type WipeStateResultState = State<WipeStateResult>;
type SignActionProofResultState = State<SignActionProofResult>;
type SignTxRequestResultState = State<SignTxRequestResult>;
type GetAddressResultState = State<GetAddressResult>;
type SignStartResultState = State<SignStartResult>;
type SignTransferResultState = State<SignTransferResult>;
type SignStreamStartResultState = State<SignStreamStartResult>;
type SweepSignStreamStartResultState = State<SweepSignStreamStartResult>;
type SignStreamTransferResultState = State<SignStreamTransferResult>;
type SignStreamFinalizeResultState = State<SignStreamFinalizeResult>;
type TxSignatureState = State<TxSignature>;
type TxSignaturesBatchState = State<Vec<TxSignature>>;
type LostAppRecoveryResultState = State<LostAppRecoveryResult>;
type LostAppRecoveryContinueResultState = State<LostAppRecoveryContinueResult>;
type LostAppRecoverySignChallengeResultState = State<LostAppRecoverySignChallengeResult>;
type RotateAppAuthKeysResultState = State<RotateAppAuthKeysResult>;
type UpgradeRotateAppAuthKeysResultState = State<UpgradeRotateAppAuthKeysResult>;
type SignChallengeAndSealSeksResultState = State<SignChallengeAndSealSeksResult>;
type RecoveryAuthorizeLostAppResultState = State<RecoveryAuthorizeLostAppResult>;
type RecoveryAuthorizeLostHwResultState = State<RecoveryAuthorizeLostHwResult>;
type UpgradeAuthorizeW3ResultState = State<UpgradeAuthorizeW3Result>;
type EekRestorationUnsealResultState = State<EekRestorationUnsealResult>;
type FullAccountCloudBackupRestorationResultState = State<FullAccountCloudBackupRestorationResult>;
type FullAccountCloudBackupRestorationContinueResultState =
    State<FullAccountCloudBackupRestorationContinueResult>;

/// Pre-computed streaming payload with commitment hash.
/// Returned by `serialize_sign_stream_payload` for the app to chunk and stream.
pub struct StreamPayload {
    pub data: Vec<u8>,
    pub commitment_hash: Vec<u8>,
    pub payload_size: u32,
}

/// Serializes transaction inputs/outputs into the canonical binary format
/// for the streaming signing protocol.
pub fn serialize_sign_stream_payload(
    version: u32,
    lock_time: u32,
    inputs: Vec<SignTxInputData>,
    outputs: Vec<SignTxOutputData>,
) -> Result<StreamPayload, CommandError> {
    let data = serialize_stream_payload(version, lock_time, &inputs, &outputs)?;
    let commitment_hash = compute_commitment_hash(&data);
    let payload_size = data.len() as u32;
    Ok(StreamPayload {
        data,
        commitment_hash,
        payload_size,
    })
}

uniffi::include_scaffolding!("firmware");
