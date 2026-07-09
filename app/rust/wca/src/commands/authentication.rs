use miniscript::DescriptorPublicKey;

use crate::{
    fwpb::get_unlock_method_rsp::UnlockMethod, signing::async_signer::derive_and_sign, yield_from_,
};
use bitcoin::{
    bip32::ChildNumber,
    hashes::{sha256, Hash},
    secp256k1::{ecdsa::Signature, PublicKey},
};
use next_gen::generator;

use crate::fwpb::{
    derive_rsp::DeriveRspStatus, wallet_rsp::Msg, DeriveKeyDescriptorCmd, DeriveRsp,
    GetUnlockMethodCmd, GetUnlockMethodRsp, LockDeviceCmd, LockDeviceRsp,
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
        Msg::DeriveRsp(DeriveRsp {
            status, descriptor, ..
        }) => match DeriveRspStatus::try_from(status) {
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
            Ok(DeriveRspStatus::Error) => Err(CommandError::DeriveKeyDescriptorFailed),
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
command!(SignChallenge = sign_challenge -> Signature, challenge: Vec<u8>, async_sign: bool);
command!(GetUnlockMethod = get_unlock_method -> UnlockInfo);
