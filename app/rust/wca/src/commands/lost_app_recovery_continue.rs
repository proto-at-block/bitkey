use bitcoin::bip32::ChildNumber;
use miniscript::DescriptorPublicKey;
use next_gen::generator;

use crate::{
    commands::find_next_bip84_derivation,
    errors::CommandError,
    fwpb::{wallet_rsp::Msg, BtcNetwork, LostAppRecoveryContinueCmd, LostAppRecoveryContinueRsp},
    wca::decode_and_check,
};

use crate::command_interface::command;

#[derive(Debug, Clone)]
pub struct LostAppRecoveryContinueResult {
    pub action_proof_signature: Vec<u8>,
    pub bare_spending_key: Vec<u8>,
    pub app_auth_key_signature: Vec<u8>,
    pub spending_key_dpub: DescriptorPublicKey,
}

/// Compute the next account index from existing descriptor public keys.
/// Uses `find_next_bip84_derivation` (from generate_keys) with the first key as `ours`.
/// Returns 0 if no existing keys are found (first keyset).
fn compute_next_account_index(
    existing_descriptor_public_keys: &[DescriptorPublicKey],
) -> Result<u32, CommandError> {
    let first = match existing_descriptor_public_keys.first() {
        Some(key) => key.clone(),
        None => return Ok(0),
    };
    let path = find_next_bip84_derivation(first, existing_descriptor_public_keys.iter().cloned())
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
    let next_account_index = compute_next_account_index(&existing_descriptor_public_keys)?;
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
    use prost::Message;

    use bitcoin::base58;

    use crate::{
        command_interface::{Command, State},
        errors::CommandError,
        fwpb::{
            wallet_rsp::Msg, BtcNetwork, DerivationPath, KeyDescriptor, LostAppRecoveryContinueRsp,
            Status, WalletRsp, Wildcard,
        },
    };

    use super::LostAppRecoveryContinue;

    fn make_response(wallet_rsp: WalletRsp) -> Vec<u8> {
        let mut buf = wallet_rsp.encode_to_vec();
        buf.extend_from_slice(&[0x90, 0x00]);
        buf
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
        command.next(Vec::default())?;

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
                    spending_key_descriptor: Some(KeyDescriptor {
                        origin_fingerprint: vec![0xe3, 0xb0, 0xc4, 0x42],
                        origin_path: Some(DerivationPath {
                            child: vec![84 | 0x80000000, 0x80000000, 0x80000000],
                            wildcard: false,
                        }),
                        xpub_path: None,
                        bare_bip32_key: base58::decode_check("xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK").unwrap(),
                        wildcard: Wildcard::Unhardened.into(),
                    }),
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
