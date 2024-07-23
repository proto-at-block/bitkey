mod csek;
mod types;

use crate::csek::{SealKey, UnsealKey};
use bitcoin::secp256k1::PublicKey;
use teltra::{TelemetryIdentifiers, Teltra, TeltraError};
use wca::attestation::{Attestation, AttestationError};
use wca::command_interface::{Command, State};
use wca::commands::{
    BioMatchStats, BtcNetwork, CancelFingerprintEnrollment, CoredumpFragment, DeleteFingerprint,
    DescriptorPublicKey, DeviceIdentifiers, DeviceInfo, EnrolledFingerprints,
    EnrollmentDiagnostics, EventFragment, FingerprintEnrollmentResult, FingerprintEnrollmentStatus,
    FirmwareFeatureFlag, FirmwareFeatureFlagCfg, FirmwareMetadata, FirmwareSlot, FwupFinish,
    FwupFinishRspStatus, FwupMode, FwupStart, FwupTransfer, GetAuthenticationKey,
    GetAuthenticationKeyV2, GetCert, GetCoredumpCount, GetCoredumpFragment, GetDeviceIdentifiers,
    GetDeviceInfo, GetEnrolledFingerprints, GetEvents, GetFingerprintEnrollmentStatus,
    GetFirmwareFeatureFlags, GetFirmwareMetadata, GetInitialSpendingKey, GetNextSpendingKey,
    GetTelemetryIdentifiers, GetUnlockMethod, LockDevice, PartiallySignedTransaction,
    QueryAuthentication, SecureBootConfig, SetFingerprintLabel, SetFirmwareFeatureFlags,
    SignChallenge, SignChallengeV2, SignTransaction, SignVerifyAttestationChallenge, Signature,
    StartFingerprintEnrollment, TemplateMatchStats, UnlockInfo, Version, WipeState,
};
use wca::errors::CommandError;
use wca::fwpb::cert_get_cmd::CertType;
use wca::fwpb::get_unlock_method_rsp::UnlockMethod;
use wca::fwpb::FingerprintHandle;
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

uniffi::include_scaffolding!("firmware");
