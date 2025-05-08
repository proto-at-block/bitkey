use miniscript::DescriptorPublicKey;

use crate::{
    fwpb::get_unlock_method_rsp::UnlockMethod, signing::async_signer::derive_and_sign, yield_from_,
    EllipticCurve, KeyEncoding, PublicKeyHandle, PublicKeyMetadata, SignatureContext,
};
use bitcoin::{
    bip32::ChildNumber,
    hashes::{sha256, Hash},
    secp256k1::{ecdsa::Signature, PublicKey},
};
use next_gen::generator;

use crate::fwpb::{
    derive_rsp::DeriveRspStatus, wallet_rsp::Msg, Curve, DeriveKeyDescriptorCmd,
    DerivePublicKeyAndSignCmd, DerivePublicKeyAndSignRsp, DerivePublicKeyCmd, DerivePublicKeyRsp,
    DeriveRsp, GetUnlockMethodCmd, GetUnlockMethodRsp, LockDeviceCmd, LockDeviceRsp,
};
use crate::{command, errors::CommandError, wca};

pub struct UnlockInfo {
    pub method: UnlockMethod,
    pub fingerprint_index: Option<u32>,
}

pub const AUTHENTICATION_DERIVATION_PATH: [ChildNumber; 2] = [
    // https://github.com/bitcoin/bips/blob/master/bip-0043.mediawiki
    // The following indexes are offsets from the lowest hardened child index (2^31),
    // so ChildNumber::Hardened { index: x } = ChildNumber::Normal { index: 2^31 + 0 }
    // Purpose: "W1HW" => [87, 49, 72, 87]
    ChildNumber::Hardened { index: 87497287 },
    // Auth key index: 0
    ChildNumber::Hardened { index: 0 },
];

pub const AUTHENTICATION_KEY_LABEL: &str = "BK-AUTH-V1";

/// NOTE: We never switched to the v2 commands.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_authentication_key_v2() -> Result<PublicKeyHandle, CommandError> {
    // The underlying API is flexible and supports more than ed25519, but
    // for now, we will only expose ed25519.
    let apdu: apdu::Command = DerivePublicKeyCmd {
        curve: Curve::Ed25519 as i32,
        label: AUTHENTICATION_KEY_LABEL.into(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    match message {
        Msg::DerivePublicKeyRsp(DerivePublicKeyRsp { pubkey, .. }) => Ok(PublicKeyHandle {
            metadata: PublicKeyMetadata {
                curve: EllipticCurve::Ed25519,
                encoding: KeyEncoding::Raw,
            },
            material: pubkey,
        }),
        _ => Err(CommandError::MissingMessage),
    }
}

/// NOTE: We never switched to the v2 commands.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_challenge_v2(challenge: Vec<u8>) -> Result<SignatureContext, CommandError> {
    let hash = <sha256::Hash as Hash>::hash(&challenge)
        .to_byte_array()
        .to_vec();
    let apdu: apdu::Command = DerivePublicKeyAndSignCmd {
        curve: Curve::Ed25519 as i32,
        label: AUTHENTICATION_KEY_LABEL.into(),
        hash,
    }
    .try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    match message {
        Msg::DerivePublicKeyAndSignRsp(DerivePublicKeyAndSignRsp {
            pubkey, signature, ..
        }) => Ok(SignatureContext {
            pubkey: Some(PublicKeyHandle {
                metadata: PublicKeyMetadata {
                    curve: EllipticCurve::Ed25519,
                    encoding: KeyEncoding::Raw,
                },
                material: pubkey,
            }),
            signature,
        }),
        _ => Err(CommandError::MissingMessage),
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_authentication_key() -> Result<PublicKey, CommandError> {
    let apdu: apdu::Command = DeriveKeyDescriptorCmd {
        derivation_path: Some(AUTHENTICATION_DERIVATION_PATH.as_ref().into()),
        ..Default::default()
    }
    .try_into()?;
    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    match message {
        Msg::DeriveRsp(DeriveRsp { status, descriptor }) => match DeriveRspStatus::try_from(status)
        {
            Ok(DeriveRspStatus::Success) => match descriptor {
                Some(descriptor) => {
                    let dpub: DescriptorPublicKey = descriptor.try_into()?;
                    match dpub {
                        DescriptorPublicKey::Single(_) => Err(CommandError::InvalidResponse),
                        DescriptorPublicKey::MultiXPub(_) => Err(CommandError::InvalidResponse),
                        DescriptorPublicKey::XPub(xpub) => Ok(xpub.xkey.public_key),
                    }
                }
                None => Err(CommandError::InvalidResponse),
            },
            Ok(DeriveRspStatus::DerivationFailed) => Err(CommandError::KeyGenerationFailed),
            Ok(DeriveRspStatus::Error) => Err(CommandError::GeneralCommandError),
            Ok(DeriveRspStatus::Unauthenticated) => Err(CommandError::Unauthenticated),
            Ok(DeriveRspStatus::Unspecified) => Err(CommandError::UnspecifiedCommandError),
            Err(_) => Err(CommandError::InvalidResponse),
        },
        _ => Err(CommandError::MissingMessage),
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn sign_challenge(challenge: Vec<u8>, async_sign: bool) -> Result<Signature, CommandError> {
    let hash = <sha256::Hash as Hash>::hash(&challenge)
        .to_byte_array()
        .to_vec();
    yield_from_!(derive_and_sign(
        hash,
        AUTHENTICATION_DERIVATION_PATH.as_ref().into(),
        async_sign
    ))
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn lock_device() -> Result<bool, CommandError> {
    let apdu: apdu::Command = LockDeviceCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::LockDeviceRsp(LockDeviceRsp {}) = message {
        Ok(true)
    } else {
        Err(CommandError::MissingMessage)
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_unlock_method() -> Result<UnlockInfo, CommandError> {
    let apdu: apdu::Command = GetUnlockMethodCmd {}.try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = wca::decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    match message {
        Msg::GetUnlockMethodRsp(GetUnlockMethodRsp {
            method,
            fingerprint_index,
        }) => Ok(UnlockInfo {
            method: match UnlockMethod::try_from(method) {
                Ok(m) => m,
                Err(_) => return Err(CommandError::InvalidResponse),
            },
            fingerprint_index: Some(fingerprint_index),
        }),
        _ => Err(CommandError::MissingMessage),
    }
}

command!(LockDevice = lock_device -> bool);
command!(GetAuthenticationKey = get_authentication_key -> PublicKey);
command!(GetAuthenticationKeyV2 = get_authentication_key_v2 -> PublicKeyHandle);
command!(SignChallenge = sign_challenge -> Signature, challenge: Vec<u8>, async_sign: bool);
command!(SignChallengeV2 = sign_challenge_v2 -> SignatureContext, challenge: Vec<u8>);
command!(GetUnlockMethod = get_unlock_method -> UnlockInfo);
