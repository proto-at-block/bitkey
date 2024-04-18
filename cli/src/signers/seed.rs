use std::sync::Arc;

use bdk::{
    bitcoin::{
        bip32::{ChildNumber, DerivationPath, ExtendedPrivKey},
        hashes::sha256,
        psbt::PartiallySignedTransaction,
        secp256k1::{
            ecdsa::Signature,
            rand::{thread_rng, Rng},
            All, Message, PublicKey, Secp256k1, SecretKey,
        },
        Network,
    },
    keys::DescriptorSecretKey,
    miniscript::descriptor::{DescriptorXKey, Wildcard},
    miniscript::DescriptorPublicKey,
    signer::{
        InputSigner, SignerCommon, SignerContext, SignerError, SignerId, SignerWrapper,
        TransactionSigner,
    },
    SignOptions,
};
use serde::{Deserialize, Serialize};
use wca::{
    commands::{find_next_bip84_derivation, AUTHENTICATION_DERIVATION_PATH},
    errors::CommandError,
    pcsc::{Transactor, TransactorError},
    signing::ExtendDerivationPath,
};

use crate::nfc::{NFCTransactions, SafeTransactor};

use super::{Authentication, Spending};

#[derive(Deserialize, Serialize, Eq)]
pub(crate) struct SeedSigner {
    #[serde(default, skip)]
    secp: Secp256k1<All>,
    seed: [u8; 32],
    network: Network,
    account: ChildNumber,
}

impl SeedSigner {
    pub(crate) fn new(network: Network, account: u32) -> Self {
        Self {
            secp: Secp256k1::new(),
            seed: thread_rng().gen(),
            network,
            account: ChildNumber::Hardened { index: account },
        }
    }

    fn master_key(&self) -> ExtendedPrivKey {
        // Normalize the network to bitcoin or testnet
        let network = match self.network {
            Network::Bitcoin => Network::Bitcoin,
            _ => Network::Testnet,
        };

        ExtendedPrivKey::new_master(network, &self.seed).expect("could not create xprv from seed")
    }

    fn authentication_private_key(&self) -> SecretKey {
        self.master_key()
            .derive_priv(&self.secp, &AUTHENTICATION_DERIVATION_PATH)
            .expect("could not derive authentication xprv")
            .private_key
    }

    fn account_private_key(&self) -> DescriptorXKey<ExtendedPrivKey> {
        let master = self.master_key();
        let path = DerivationPath::from_iter(bip84(self.network, self.account));
        let xkey = master
            .derive_priv(&self.secp, &path)
            .expect("could not derive bip84 xprv");
        let origin = (xkey.fingerprint(&self.secp), path);

        DescriptorXKey {
            origin: Some(origin),
            xkey,
            derivation_path: DerivationPath::master(),
            wildcard: Wildcard::Unhardened,
        }
    }

    fn account_public_key(&self) -> DescriptorPublicKey {
        DescriptorSecretKey::XPrv(self.account_private_key())
            .to_public(&self.secp)
            .expect("could not derive dpub from dprv")
    }
}

fn bip84(network: Network, account: ChildNumber) -> [ChildNumber; 3] {
    [
        ChildNumber::Hardened { index: 84 },
        match network {
            Network::Bitcoin => ChildNumber::Hardened { index: 0 },
            _ => ChildNumber::Hardened { index: 1 },
        },
        account,
    ]
}

impl Authentication for SeedSigner {
    fn public_key(&self) -> PublicKey {
        self.authentication_private_key().public_key(&self.secp)
    }

    fn sign(&self, message: &[u8], _: &impl Transactor) -> Result<Signature, TransactorError> {
        let message = Message::from_hashed_data::<sha256::Hash>(message);
        Ok(self
            .secp
            .sign_ecdsa(&message, &self.authentication_private_key()))
    }
}

impl Spending for SeedSigner {
    fn public_key(&self) -> DescriptorPublicKey {
        self.account_public_key()
    }

    fn next(
        &self,
        seen: impl Iterator<Item = DescriptorPublicKey>,
        _: &impl NFCTransactions,
    ) -> Result<Self, TransactorError> {
        let next_path = find_next_bip84_derivation(self.account_public_key(), seen).ok_or(
            TransactorError::CommandError(CommandError::InvalidArguments),
        )?;
        let [_purpose, _coin_type, next_account] = next_path;
        Ok(Self {
            secp: Secp256k1::new(),
            account: next_account,
            ..*self
        })
    }

    fn signer(&self, _: &SafeTransactor) -> Arc<dyn TransactionSigner> {
        Arc::new(SeedBDKSigner::new(self.account_private_key()))
    }
}

impl PartialEq for SeedSigner {
    fn eq(&self, other: &Self) -> bool {
        // Compare everything except the secp context
        let x = (self.seed, self.network, self.account);
        let y = (other.seed, other.network, other.account);
        x == y
    }
}

#[derive(Debug)]
struct SeedBDKSigner {
    id: SignerId,
    spending: SignerWrapper<DescriptorXKey<ExtendedPrivKey>>,
    change: SignerWrapper<DescriptorXKey<ExtendedPrivKey>>,
}

impl SeedBDKSigner {
    pub fn new(key: DescriptorXKey<ExtendedPrivKey>) -> Self {
        let fingerprint = match key.origin {
            Some((fingerprint, _)) => fingerprint,
            None => key.xkey.fingerprint(&Secp256k1::new()),
        };

        let account_dsk = DescriptorSecretKey::XPrv(key);
        let spending = match account_dsk.extend_derivation_path(&[ChildNumber::Normal { index: 0 }])
        {
            DescriptorSecretKey::Single(_) => unimplemented!(),
            DescriptorSecretKey::MultiXPrv(_) => unimplemented!(),
            DescriptorSecretKey::XPrv(xprv) => xprv,
        };
        let change = match account_dsk.extend_derivation_path(&[ChildNumber::Normal { index: 1 }]) {
            DescriptorSecretKey::Single(_) => unimplemented!(),
            DescriptorSecretKey::MultiXPrv(_) => unimplemented!(),
            DescriptorSecretKey::XPrv(xprv) => xprv,
        };

        Self {
            id: SignerId::Fingerprint(fingerprint),
            spending: SignerWrapper::new(spending, SignerContext::Segwitv0),
            change: SignerWrapper::new(change, SignerContext::Segwitv0),
        }
    }
}

impl SignerCommon for SeedBDKSigner {
    fn id(&self, _: &Secp256k1<All>) -> SignerId {
        self.id.clone()
    }

    fn descriptor_secret_key(&self) -> Option<DescriptorSecretKey> {
        None
    }
}

impl InputSigner for SeedBDKSigner {
    fn sign_input(
        &self,
        psbt: &mut PartiallySignedTransaction,
        input_index: usize,
        sign_options: &SignOptions,
        secp: &Secp256k1<All>,
    ) -> Result<(), SignerError> {
        self.spending
            .sign_input(psbt, input_index, sign_options, secp)?;
        self.change
            .sign_input(psbt, input_index, sign_options, secp)?;
        Ok(())
    }
}
