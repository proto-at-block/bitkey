use bitcoin::bip32::ChildNumber;
use miniscript::DescriptorPublicKey;
use next_gen::generator;

use crate::{
    commands::{find_next_bip84_derivation, generate_keys::get_initial_spending_key_parsed},
    errors::CommandError,
    fwpb::{wallet_rsp::Msg, BtcNetwork, LostAppRecoveryContinueCmd, LostAppRecoveryContinueRsp},
    wca::decode_and_check,
    yield_from_,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub struct LostAppRecoveryContinueResult {
    pub action_proof_signature: Vec<u8>,
    pub bare_spending_key: Vec<u8>,
    pub app_auth_key_signature: Vec<u8>,
    pub spending_key_dpub: DescriptorPublicKey,
}

/// Compute the next account index from existing descriptor public keys using
/// the current device's BIP84 lineage as the anchor. This matches
/// `GetNextSpendingKey` and avoids treating an older hardware lineage as
/// canonical when recovery history contains mixed keysets.
fn compute_next_account_index(
    ours: DescriptorPublicKey,
    existing_descriptor_public_keys: &[DescriptorPublicKey],
) -> Result<u32, CommandError> {
    let path = find_next_bip84_derivation(ours, existing_descriptor_public_keys.iter().cloned())
        .ok_or(CommandError::InvalidArguments)?;
    match path[2] {
        ChildNumber::Hardened { index } => Ok(index),
        ChildNumber::Normal { .. } => Err(CommandError::InvalidArguments),
    }
}

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn lost_app_recovery_continue(
    action_proof_version: u32,
    action: String,
    value: Option<String>,
    bindings: String,
    existing_descriptor_public_keys: Vec<DescriptorPublicKey>,
    network: BtcNetwork,
    app_global_auth_key: Vec<u8>,
) -> Result<LostAppRecoveryContinueResult, CommandError> {
    let (ours_dpub, _) = yield_from_!(get_initial_spending_key_parsed(network))?;
    let next_account_index =
        compute_next_account_index(ours_dpub, &existing_descriptor_public_keys)?;
    let apdu: apdu::Command = LostAppRecoveryContinueCmd {
        action_proof_version,
        action,
        value: value.unwrap_or_default(),
        bindings,
        next_account_index,
        network: network.into(),
        app_global_auth_key,
    }
    .try_into()?;

    let data = yield_!(apdu.into());
    let response = apdu::Response::from(data);
    let message = decode_and_check(response)?
        .msg
        .ok_or(CommandError::MissingMessage)?;

    if let Msg::LostAppRecoveryContinueRsp(LostAppRecoveryContinueRsp {
        action_proof_signature,
        bare_spending_key,
        app_auth_key_signature,
        spending_key_descriptor,
    }) = message
    {
        let spending_key_dpub = spending_key_descriptor
            .ok_or(CommandError::MissingMessage)?
            .try_into()?;
        Ok(LostAppRecoveryContinueResult {
            action_proof_signature,
            bare_spending_key,
            app_auth_key_signature,
            spending_key_dpub,
        })
    } else {
        Err(CommandError::MissingMessage)
    }
}

command!(LostAppRecoveryContinue = lost_app_recovery_continue -> LostAppRecoveryContinueResult,
    action_proof_version: u32,
    action: String,
    value: Option<String>,
    bindings: String,
    existing_descriptor_public_keys: Vec<DescriptorPublicKey>,
    network: BtcNetwork,
    app_global_auth_key: Vec<u8>
);

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use prost::Message;

    use bitcoin::base58;
    use miniscript::DescriptorPublicKey;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{
            derive_rsp::DeriveRspStatus, wallet_rsp::Msg, BtcNetwork, DerivationPath, DeriveRsp,
            KeyDescriptor, LostAppRecoveryContinueRsp, Status, WalletRsp, Wildcard,
        },
    };

    use super::{compute_next_account_index, LostAppRecoveryContinue};

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

    #[test]
    fn compute_next_account_index_uses_current_hardware_lineage() -> Result<(), CommandError> {
        let ours_0 = DescriptorPublicKey::from_str(
            "[0c5f9a1e/84'/1'/0']tpubDCxzhZZE31g2EqSv1UajMAw5Hd62htydz9r2XBkrccHgBh8uw3n62zr6Zjmj64tfTk8Tjxo6VctjUMAh5DXWTErfQPC6RmQhTdtNnXuTXTQ/*",
        )
        .unwrap();
        let ours_1 = DescriptorPublicKey::from_str(
            "[0c5f9a1e/84'/1'/1']tpubDCxzhZZE31g2GPc7WcCG4gEwMMTxB9uAcLKuGtbi4n5uQKGLaaNAbTZmcK4Rq6pCesEitB7PV9k1hXs7qU8YTXXfd2LpVXmpUT9FcsvEXC3/*",
        )
        .unwrap();
        let other_0 = DescriptorPublicKey::from_str(
            "[51135a9c/84'/1'/0']tpubDCUBn4Wj3t577bANcZqscxNH14vPuXm2L5vM6dcvdfqfcYDLCRFhZAqBvEjuPh2yWL8Sjbpa6HhaDEUG9iSVhANhyruL5Wcfz2DeR9Hf7cr/*",
        )
        .unwrap();

        let next_account_index = compute_next_account_index(ours_0, &[other_0, ours_1])?;
        assert_eq!(next_account_index, 2);

        Ok(())
    }

    #[test]
    fn lost_app_recovery_continue_success() -> Result<(), CommandError> {
        let command = LostAppRecoveryContinue::new(
            1,
            "CreateLostAppRecovery".to_string(),
            None,
            "n=nonce1,tb=tokenbinding".to_string(),
            vec![], // no existing keys → next_account_index = 0
            BtcNetwork::Bitcoin,
            vec![0x02; 33],
        );
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

        match command.next(initial_key_response) {
            Ok(State::Data { .. }) => {}
            other => panic!(
                "Expected lost app recovery continue request, got {:?}",
                other
            ),
        }

        let action_proof_signature = vec![0xAA; 64];
        let bare_spending_key = vec![0xBB; 78];
        let app_auth_key_signature = vec![0xCC; 64];

        let response = make_response(WalletRsp {
            status: Status::Success.into(),
            msg: Some(Msg::LostAppRecoveryContinueRsp(
                LostAppRecoveryContinueRsp {
                    action_proof_signature: action_proof_signature.clone(),
                    bare_spending_key: bare_spending_key.clone(),
                    app_auth_key_signature: app_auth_key_signature.clone(),
                    #[allow(deprecated)]
                    spending_key_descriptor: Some(descriptor(0, [0xe3, 0xb0, 0xc4, 0x42])),
                },
            )),
            ..Default::default()
        });

        match command.next(response) {
            Ok(State::Result { value }) => {
                assert_eq!(value.action_proof_signature, action_proof_signature);
                assert_eq!(value.bare_spending_key, bare_spending_key);
                assert_eq!(value.app_auth_key_signature, app_auth_key_signature);
            }
            other => panic!("Expected Success, got {:?}", other),
        }

        Ok(())
    }
}
