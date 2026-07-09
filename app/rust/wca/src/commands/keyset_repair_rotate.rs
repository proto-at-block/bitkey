use bitcoin::hashes::{sha256, Hash};
use miniscript::DescriptorPublicKey;
use next_gen::generator;

use crate::{
    commands::generate_keys::find_next_bip84_derivation,
    errors::CommandError,
    fwpb::{BtcNetwork, KeysetRepairRotateHwKeyCmd, Status},
    wca::decode_and_check,
    yield_from_,
};

use crate::command_interface::command;
use crate::commands::generate_keys::get_initial_spending_key_parsed;

#[derive(Debug, Clone)]
pub enum KeysetRepairRotateHwKeyResult {
    ConfirmationPending {
        response_handle: Vec<u8>,
        confirmation_handle: Vec<u8>,
    },
}

/// Sends the keyset-repair rotate composite command to firmware. Bundles the next HW
/// spending key derivation and access-token signing into a single confirmable tap.
///
/// The Rust binding pre-derives the master spending key (a non-confirmable firmware
/// derive) so it can compute the next BIP84 account index from the app-supplied list
/// of existing keys, then issues the composite confirmable command. Firmware shows a
/// confirmation prompt; returns ConfirmationPending with handles. After user confirms,
/// the caller uses GetConfirmationResult to retrieve the spending key descriptor and
/// access-token signature. The app-facing API accepts the raw access token, but
/// the NFC command carries only sha256(access token) because that is the digest
/// verified by the server proof-of-possession path.
#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn keyset_repair_rotate_hw_key(
    access_token: Vec<u8>,
    existing_descriptor_public_keys: Vec<DescriptorPublicKey>,
    network: BtcNetwork,
) -> Result<KeysetRepairRotateHwKeyResult, CommandError> {
    // Pre-derive master to compute next account index. Same approach as
    // lost_app_recovery_continue. Not a confirmable op; firmware just returns the dpub.
    let (ours_dpub, _) = yield_from_!(get_initial_spending_key_parsed(network))?;
    let path = find_next_bip84_derivation(ours_dpub, existing_descriptor_public_keys.into_iter())
        .ok_or(CommandError::InvalidArguments)?;
    let next_account_index = match path[2] {
        bitcoin::bip32::ChildNumber::Hardened { index } => index,
        bitcoin::bip32::ChildNumber::Normal { .. } => return Err(CommandError::InvalidArguments),
    };

    let access_token_hash = sha256::Hash::hash(&access_token).to_byte_array().to_vec();
    let apdu: apdu::Command = KeysetRepairRotateHwKeyCmd {
        access_token_hash,
        next_account_index,
        network: network.into(),
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let wallet_rsp = decode_and_check(response)?;

    if Status::try_from(wallet_rsp.status) == Ok(Status::ConfirmationPending) {
        return Ok(KeysetRepairRotateHwKeyResult::ConfirmationPending {
            response_handle: wallet_rsp.response_handle,
            confirmation_handle: wallet_rsp.confirmation_handle,
        });
    }

    Err(CommandError::InvalidResponse)
}

command!(KeysetRepairRotateHwKey = keyset_repair_rotate_hw_key -> KeysetRepairRotateHwKeyResult,
    access_token: Vec<u8>,
    existing_descriptor_public_keys: Vec<DescriptorPublicKey>,
    network: BtcNetwork
);

#[cfg(test)]
mod tests {
    use bitcoin::base58;
    use bitcoin::hashes::{sha256, Hash};
    use prost::Message;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{
            derive_rsp::DeriveRspStatus, wallet_rsp::Msg, BtcNetwork, DerivationPath, DeriveRsp,
            KeyDescriptor, Status, WalletRsp, Wildcard,
        },
    };

    use super::KeysetRepairRotateHwKey;

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
    }

    #[allow(deprecated)]
    fn descriptor(account_index: u32, origin_fingerprint: [u8; 4]) -> KeyDescriptor {
        KeyDescriptor {
            origin_fingerprint: origin_fingerprint.to_vec(),
            origin_path: Some(DerivationPath {
                child: vec![
                    84 | 0x8000_0000,
                    0x8000_0000,
                    account_index | 0x8000_0000,
                ],
                wildcard: false,
            }),
            xpub_path: None,
            bare_bip32_key: base58::decode_check("xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK").unwrap(),
            wildcard: Wildcard::Unhardened.into(),
        }
    }

    fn contains(haystack: &[u8], needle: &[u8]) -> bool {
        haystack
            .windows(needle.len())
            .any(|window| window == needle)
    }

    #[test]
    fn sends_access_token_hash_not_raw_token() -> Result<(), CommandError> {
        let access_token = b"header.payload.signature-that-is-longer-than-a-sha256-digest".to_vec();
        let command =
            KeysetRepairRotateHwKey::new(access_token.clone(), vec![], BtcNetwork::Bitcoin);

        match command.next(Vec::default()) {
            Ok(State::Data { .. }) => {}
            other => panic!("Expected initial spending key request, got {:?}", other),
        }

        let initial_key_response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::DeriveRsp(DeriveRsp {
                status: DeriveRspStatus::Success.into(),
                descriptor: Some(descriptor(0, [0xde, 0xad, 0xbe, 0xef])),
                ..Default::default()
            })),
            ..Default::default()
        });

        let keyset_repair_request = match command.next(initial_key_response)? {
            State::Data { response } => response,
            other => panic!("Expected keyset repair rotate request, got {:?}", other),
        };

        let access_token_hash = sha256::Hash::hash(&access_token).to_byte_array();
        assert!(contains(&keyset_repair_request, &access_token_hash));
        assert!(!contains(&keyset_repair_request, &access_token));

        Ok(())
    }
}
