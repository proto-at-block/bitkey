mod csek;
mod types;

use crate::csek::{SealKey, UnsealKey};
use crypto::chacha20poly1305::{ChaCha20Poly1305Error, XChaCha20Poly1305};
use crypto::crypto_box::{CryptoBox, CryptoBoxError, CryptoBoxKeyPair};
use crypto::ecdh::Secp256k1SharedSecret;
use crypto::hkdf::{Hkdf, HkdfError};
use crypto::invoice::{Invoice, InvoiceError, Sha256};
use crypto::keys::{PublicKey, SecretKey, SecretKeyError};
use crypto::signature_verifier::{SignatureVerifier, SignatureVerifierError};
use crypto::spake2::{Spake2Context, Spake2Error, Spake2Keys, Spake2Role};
use crypto::wsm_integrity::{WsmContext, WsmIntegrityVerifier, WsmIntegrityVerifierError};
use teltra::{TelemetryIdentifiers, Teltra, TeltraError};
use wca::attestation::{Attestation, AttestationError};
use wca::command_interface::{Command, State};
use wca::commands::{
    BtcNetwork, CoredumpFragment, DeleteFingerprint, DescriptorPublicKey, DeviceIdentifiers,
    DeviceInfo, EnrolledFingerprints, EventFragment, FingerprintEnrollmentStatus,
    FirmwareFeatureFlag, FirmwareFeatureFlagCfg, FirmwareMetadata, FirmwareSlot, FwupFinish,
    FwupFinishRspStatus, FwupMode, FwupStart, FwupTransfer, GetAuthenticationKey,
    GetAuthenticationKeyV2, GetCert, GetCoredumpCount, GetCoredumpFragment, GetDeviceIdentifiers,
    GetDeviceInfo, GetEnrolledFingerprints, GetEvents, GetFingerprintEnrollmentStatus,
    GetFirmwareFeatureFlags, GetFirmwareMetadata, GetInitialSpendingKey, GetNextSpendingKey,
    GetTelemetryIdentifiers, GetUnlockMethod, LockDevice, PartiallySignedTransaction,
    QueryAuthentication, SecureBootConfig, SetFingerprintLabel, SetFirmwareFeatureFlags,
    SignChallenge, SignChallengeV2, SignTransaction, SignVerifyAttestationChallenge, Signature,
    StartFingerprintEnrollment, UnlockInfo, Version, WipeState,
};
use wca::fwpb::cert_get_cmd::CertType;
use wca::fwpb::get_unlock_method_rsp::UnlockMethod;
use wca::fwpb::FingerprintHandle;
use wca::{EllipticCurve, KeyEncoding, PublicKeyHandle, PublicKeyMetadata, SignatureContext};

use wca::errors::CommandError;

type BooleanState = State<bool>;
type U16State = State<u16>;
type PartiallySignedTransactionState = State<PartiallySignedTransaction>;
type FingerprintEnrollmentStatusState = State<FingerprintEnrollmentStatus>;
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

uniffi::include_scaffolding!("core");
