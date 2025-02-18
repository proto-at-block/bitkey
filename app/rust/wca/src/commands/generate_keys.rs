use bitcoin::bip32::{ChildNumber, DerivationPath};
use miniscript::DescriptorPublicKey;
use next_gen::generator;

use crate::fwpb::derive_rsp::DeriveRspStatus;
use crate::fwpb::wallet_rsp::Msg;
use crate::fwpb::{BtcNetwork, DeriveKeyDescriptorCmd, DeriveRsp};
use crate::wca;
use crate::yield_from_;
use crate::{errors::CommandError, fwpb};

use crate::command_interface::command;

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
pub(crate) fn derive(
    network: fwpb::BtcNetwork,
    derivation_path: &DerivationPath,
) -> Result<DescriptorPublicKey, CommandError> {
    let apdu: apdu::Command = DeriveKeyDescriptorCmd {
        network: network.into(),
        derivation_path: Some(derivation_path.into()),
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
                Some(descriptor) => Ok(descriptor.try_into()?),
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
fn get_initial_spending_key(network: BtcNetwork) -> Result<DescriptorPublicKey, CommandError> {
    let purpose = ChildNumber::Hardened { index: 84 };
    let coin_type = ChildNumber::Hardened {
        index: match network {
            BtcNetwork::Bitcoin => 0,
            _ => 1,
        },
    };
    let account = ChildNumber::Hardened { index: 0 };
    let derivation_path = [purpose, coin_type, account].as_ref().into();
    yield_from_!(derive(network, &derivation_path))
}

command!(GetInitialSpendingKey = get_initial_spending_key -> DescriptorPublicKey, network: fwpb::BtcNetwork);

#[generator(yield(Vec<u8>), resume(Vec<u8>))]
fn get_next_spending_key(
    seen: Vec<DescriptorPublicKey>,
    network: BtcNetwork,
) -> Result<DescriptorPublicKey, CommandError> {
    let ours = yield_from_!(get_initial_spending_key(network))?;
    let next = find_next_bip84_derivation(ours, seen.into_iter())
        .ok_or(CommandError::InvalidArguments)?
        .as_slice()
        .into();
    yield_from_!(derive(network, &next))
}

pub fn find_next_bip84_derivation(
    ours: DescriptorPublicKey,
    seen: impl Iterator<Item = DescriptorPublicKey>,
) -> Option<[ChildNumber; 3]> {
    let path_ours = bip84(&ours)?;
    let max = seen
        .filter(|seen| seen.master_fingerprint() == ours.master_fingerprint())
        .filter_map(|seen| bip84(&seen))
        .filter(|path_seen| path_seen[..2] == path_ours[..2])
        .max_by_key(|[_, _, account]| *account)
        .map(|[_, _, account]| account);

    match max {
        Some(account) => {
            let [purpose, coin_type, _] = path_ours;
            let next = account.increment().ok()?;
            Some([purpose, coin_type, next])
        }
        None => Some(path_ours),
    }
}

fn bip84(dpub: &DescriptorPublicKey) -> Option<[ChildNumber; 3]> {
    match dpub.full_derivation_path()?.into_iter().as_slice() {
        [purpose @ ChildNumber::Hardened { index: 84 }, coin_type @ ChildNumber::Hardened { index: 0 | 1 }, account, ..] => {
            Some([*purpose, *coin_type, *account])
        }
        _ => None,
    }
}

command!(GetNextSpendingKey = get_next_spending_key -> DescriptorPublicKey, seen: Vec<DescriptorPublicKey>, network: fwpb::BtcNetwork);

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use bitcoin::{
        bip32::ChildNumber,
        secp256k1::rand::{seq::SliceRandom, thread_rng},
    };
    use miniscript::DescriptorPublicKey;

    use super::find_next_bip84_derivation;

    #[test]
    fn finds_next_bip84_derivation() {
        // Master: [0c5f9a1e]tprv8ZgxMBicQKsPd7Uf69XL1XwhmjHopUGep8GuEiJDZmbQz6o58LninorQAfcKZWARbtRtfnLcJ5MQ2AtHcQJCCRUcMRvmDUjyEmNUWwx8UbK
        // via "crazy horse battery staple" from http://bip32.org/
        let dpub_ours_0 = DescriptorPublicKey::from_str("[0c5f9a1e/84'/1'/0']tpubDCxzhZZE31g2EqSv1UajMAw5Hd62htydz9r2XBkrccHgBh8uw3n62zr6Zjmj64tfTk8Tjxo6VctjUMAh5DXWTErfQPC6RmQhTdtNnXuTXTQ/*").unwrap();
        let dpub_ours_1 = DescriptorPublicKey::from_str("[0c5f9a1e/84'/1'/1']tpubDCxzhZZE31g2GPc7WcCG4gEwMMTxB9uAcLKuGtbi4n5uQKGLaaNAbTZmcK4Rq6pCesEitB7PV9k1hXs7qU8YTXXfd2LpVXmpUT9FcsvEXC3/*").unwrap();
        let dpub_ours_2 = DescriptorPublicKey::from_str("[0c5f9a1e/84'/1'/2']tpubDCxzhZZE31g2HvAVfbRdbkwV8kssfkzgqB25yaHNRLgLLyQBam5qzcNknfMBEPhDUjnDKa9PqUxvFy5zhAGaorhpNWioB7m3w8WZhUn3Mig/*").unwrap();

        // Master: [51135a9c]tprv8ZgxMBicQKsPeF7YCZYaHh74o8H8F53qrwwPuDZHhA5D4VFoyREAt4kJjGg9RXjK2n6LHr66TyTem4EMgTgRRC4Kmphsuxw5CdHLRNKTjmh
        // "abandon abandon abandon abandon" from http://bip32.org/
        let dpub_theirs_0 = DescriptorPublicKey::from_str("[51135a9c/84'/1'/0']tpubDCUBn4Wj3t577bANcZqscxNH14vPuXm2L5vM6dcvdfqfcYDLCRFhZAqBvEjuPh2yWL8Sjbpa6HhaDEUG9iSVhANhyruL5Wcfz2DeR9Hf7cr/*").unwrap();
        let dpub_theirs_1 = DescriptorPublicKey::from_str("[51135a9c/84'/1'/1']tpubDCUBn4Wj3t579LYCtufppSACsmL1XFvdU37KrVJjfDvXVPRbESvc1TjzaMnfk9TnqHGfKbFM6M4BbnFoqaB2RrRnqbo3aqPzABJMJJDrLWi/*").unwrap();
        let dpub_theirs_2 = DescriptorPublicKey::from_str("[51135a9c/84'/1'/2']tpubDCUBn4Wj3t57AzEk3jta2oyM8XBU2dPw9Hs6iwqRqEQL1eWTFFt1w3Fi9At7AmjLrtNrrufr9Vrbnyky2y94DpfHq4hGLZXVmoND4bCcKLf/*").unwrap();

        let ours = dpub_ours_0.clone();
        let seen = [];
        assert_eq!(
            [
                ChildNumber::Hardened { index: 84 },
                ChildNumber::Hardened { index: 1 },
                ChildNumber::Hardened { index: 0 }
            ],
            find_next_bip84_derivation(ours, seen.into_iter()).unwrap(),
            "Return the template key if there are no seen keys"
        );

        let ours = dpub_ours_0.clone();
        let seen = [dpub_ours_0.clone()];
        assert_eq!(
            [
                ChildNumber::Hardened { index: 84 },
                ChildNumber::Hardened { index: 1 },
                ChildNumber::Hardened { index: 1 }
            ],
            find_next_bip84_derivation(ours, seen.into_iter()).unwrap(),
            "Increment the template key in the base case"
        );

        let ours = dpub_ours_0.clone();
        let seen = [dpub_ours_2.clone()];
        assert_eq!(
            [
                ChildNumber::Hardened { index: 84 },
                ChildNumber::Hardened { index: 1 },
                ChildNumber::Hardened { index: 3 }
            ],
            find_next_bip84_derivation(ours, seen.into_iter()).unwrap(),
            "Increment over latest seen key"
        );

        let ours = dpub_ours_0.clone();
        let seen = [
            dpub_ours_0.clone(),
            dpub_theirs_1.clone(),
            dpub_ours_2.clone(),
        ];
        assert_eq!(
            [
                ChildNumber::Hardened { index: 84 },
                ChildNumber::Hardened { index: 1 },
                ChildNumber::Hardened { index: 3 }
            ],
            find_next_bip84_derivation(ours, seen.into_iter()).unwrap(),
            "Skip gaps in the seen keys",
        );

        let ours = dpub_ours_0.clone();
        let seen = [
            dpub_theirs_0.clone(),
            dpub_theirs_1.clone(),
            dpub_theirs_2.clone(),
        ];
        assert_eq!(
            [
                ChildNumber::Hardened { index: 84 },
                ChildNumber::Hardened { index: 1 },
                ChildNumber::Hardened { index: 0 }
            ],
            find_next_bip84_derivation(ours, seen.into_iter()).unwrap(),
            "Ignore unrelated keys",
        );

        let ours = dpub_ours_0.clone();
        let mut seen = [
            dpub_ours_0,
            dpub_ours_1,
            dpub_ours_2,
            dpub_theirs_0,
            dpub_theirs_1,
            dpub_theirs_2,
        ];
        seen.shuffle(&mut thread_rng());
        assert_eq!(
            [
                ChildNumber::Hardened { index: 84 },
                ChildNumber::Hardened { index: 1 },
                ChildNumber::Hardened { index: 3 }
            ],
            find_next_bip84_derivation(ours, seen.into_iter()).unwrap(),
            "A full set of seen keys, out of order",
        );
    }
}
