use std::{num::TryFromIntError, str::Utf8Error, sync::PoisonError};

use bitcoin::util::psbt::PsbtParseError;
use miniscript::descriptor::DescriptorKeyParseError;
use thiserror::Error;

#[derive(Error, Debug)]
pub enum CommandError {
    #[error("invalid arguments")]
    InvalidArguments,
    #[error(transparent)]
    EncodeError(#[from] EncodeError),
    #[error("corrupt response")]
    InvalidResponse,
    #[error(transparent)]
    CorruptResponseEnvelope(#[from] prost::DecodeError),
    #[error(transparent)]
    CorruptResponsePayload(#[from] DecodeError),
    #[error("response is missing an expected message payload")]
    MissingMessage,
    #[error("lock error: {0}")]
    PoisonedLockError(String),
    #[error("command was unsuccessful: unspecified error")]
    UnspecifiedCommandError,
    #[error("command was unsuccessful: general error")]
    GeneralCommandError,
    #[error("signing error")]
    SigningError,
    #[error("command was unsuccessful: not authenticated to hardware")]
    Unauthenticated,
    #[error("command was unsuccessful: unimplemented error")]
    Unimplemented,
    #[error(transparent)]
    ECDSASigningError(#[from] bitcoin::secp256k1::Error),
    #[error(transparent)]
    DescriptorKeyParseError(#[from] DescriptorKeyParseError),
    #[error(transparent)]
    PsbtParseError(#[from] PsbtParseError),
    #[error("SealCsekResponse error: Seal Error")]
    SealCsekResponseSealError,
    #[error("UnsealCsekResponse error: Unseal Error")]
    SealCsekResponseUnsealError,
    #[error("UnealCsekResponse error: Unauthenticated")]
    SealCsekResponseUnauthenticatedError,
    #[error("invalid key size: {}", .0.len())]
    KeySizeError(Vec<u8>),
    #[error("signature invalid")]
    SignatureInvalid,
    #[error("version invalid")]
    VersionInvalid,
    #[error("key generation failed")]
    KeyGenerationFailed,
    #[error(transparent)]
    PSBTSigningError(#[from] crate::signing::Error),
    #[error("failed to get metadata")]
    MetadataError,
    #[error("failed to get battery charge")]
    BatteryError,
    #[error("failed to get serial number")]
    SerialError,
    #[error("firmware received an unknown message")]
    UnknownMessage,
    #[error("secure channel not established yet")]
    NoSecureChannel,
    #[error("key derivation failed")]
    KeyDerivationFailed,
    #[error("secure channel error")]
    SecureChannelError,
    #[error("wrong secret")]
    WrongSecret,
    #[error("flash storage error")]
    StorageErr,
    #[error("no secret provisioned")]
    NoSecretProvisioned,
    #[error("waiting on delay")]
    WaitingOnDelay,
    #[error("feature not supported")]
    FeatureNotSupported,
    #[error("cert read fail")]
    CertReadFail,
    #[error("attestation error")]
    AttestationError,
}

impl<T> From<PoisonError<T>> for CommandError {
    fn from(err: PoisonError<T>) -> Self {
        CommandError::PoisonedLockError(err.to_string())
    }
}

#[derive(Error, Debug)]
pub enum DecodeError {
    #[error("no key bundle")]
    MissingKeyBundle,
    #[error("no key descriptor")]
    MissingKeyDescriptor,
    #[error("key descriptor missing: origin_path")]
    MissingOriginPath,
    #[error("key descriptor missing: xpub_path")]
    MissingXPubPath,
    #[error("descriptor key internal parse error: {0}")]
    DescriptorKeyInternalParseError(#[from] DescriptorKeyParseError),
    #[error("public key internal parse error: {0}")]
    PublicKeyInternalParseError(#[from] bitcoin::secp256k1::Error),
    #[error("invalid key prefix")]
    InvalidPrefix(#[from] Utf8Error),
}

#[derive(Error, Debug)]
pub enum EncodeError {
    #[error("truncated encoded proto")]
    TruncatedProto,
    #[error("oversize encoded proto")]
    OversizeProto(#[from] TryFromIntError),
}
