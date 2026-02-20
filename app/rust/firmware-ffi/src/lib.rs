mod csek;
mod types;

use crate::csek::{SealKey, UnsealKey};
use bitcoin::{bip32::Fingerprint, secp256k1::PublicKey};
use teltra::{TelemetryIdentifiers, Teltra, TeltraError};
use wca::attestation::{Attestation, AttestationError};
use wca::command_interface::{Command, State};
use wca::commands::{
    BioMatchStats, BtcNetwork, CancelFingerprintEnrollment, ChunkData, ConfirmedCommandResult,
    CoredumpFragment, DeleteFingerprint, DescriptorPublicKey, DeviceIdentifiers, DeviceInfo,
    DeviceInfoMcu, EnrolledFingerprints, EnrollmentDiagnostics, EventFragment,
    FingerprintEnrollmentResult, FingerprintEnrollmentStatus, FingerprintResetFinalize,
    FingerprintResetRequest, FirmwareFeatureFlag, FirmwareFeatureFlagCfg, FirmwareMetadata,
    FirmwareSlot, FwupFinish, FwupFinishRspStatus, FwupMode, FwupStart, FwupStartResult,
    FwupTransfer, GetAddress, GetAddressResult, GetAuthenticationKey, GetAuthenticationKeyV2,
    GetCert, GetConfirmationResult, GetConfirmationResultChunk, GetCoredumpCount,
    GetCoredumpFragment, GetDeviceIdentifiers, GetDeviceInfo, GetEnrolledFingerprints, GetEvents,
    GetFingerprintEnrollmentStatus, GetFirmwareFeatureFlags, GetFirmwareMetadata,
    GetInitialSpendingKey, GetNextSpendingKey, GetTelemetryIdentifiers, GetUnlockMethod,
    LockDevice, McuInfo, McuName, McuRole, PartiallySignedTransaction, ProvisionAppAuthKey,
    QueryAuthentication, SecureBootConfig, SetFingerprintLabel, SetFirmwareFeatureFlags,
    SignActionProof, SignActionProofResult, SignChallenge, SignChallengeV2, SignStart,
    SignStartResult, SignTransaction, SignTransfer, SignTransferResult,
    SignVerifyAttestationChallenge, Signature, StartFingerprintEnrollment, TemplateMatchStats,
    UnlockInfo, VerifyKeysAndBuildDescriptor, Version, WipeState, WipeStateResult,
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
type PublicKeyHandleState = State<PublicKeyHandle>;
type SignatureContextState = State<SignatureContext>;
type EnrolledFingerprintsState = State<EnrolledFingerprints>;
type UnlockInfoState = State<UnlockInfo>;
type ConfirmedCommandResultState = State<ConfirmedCommandResult>;
type FwupStartResultState = State<FwupStartResult>;
type WipeStateResultState = State<WipeStateResult>;
type SignActionProofResultState = State<SignActionProofResult>;
type GetAddressResultState = State<GetAddressResult>;
type SignStartResultState = State<SignStartResult>;
type SignTransferResultState = State<SignTransferResult>;
type ChunkDataState = State<ChunkData>;

uniffi::include_scaffolding!("firmware");
