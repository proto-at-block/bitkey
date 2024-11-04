use std::{
    fmt::{Debug, Formatter},
    result::Result,
    sync::Arc,
};

use bdk::{
    bitcoin::{
        psbt::PartiallySignedTransaction,
        secp256k1::{ecdsa::Signature, All, PublicKey, Secp256k1},
        Network,
    },
    keys::DescriptorSecretKey,
    miniscript::DescriptorPublicKey,
    signer::{SignerCommon, SignerError, SignerId, TransactionSigner},
    SignOptions,
};
use serde::{Deserialize, Serialize};
use wca::pcsc::{Transactor, TransactorError};

use crate::{
    nfc::{NFCTransactions, PairingError, SafeTransactor},
    serde_helpers,
};

use super::{Authentication, Spending};

#[derive(Deserialize, Serialize, PartialEq, Eq)]
pub(crate) struct HardwareSigner {
    authentication: PublicKey,
    #[serde(with = "serde_helpers::string")]
    spending: DescriptorPublicKey,
}

impl HardwareSigner {
    pub(crate) fn new(network: Network, context: &impl Transactor) -> Result<Self, PairingError> {
        Ok(Self {
            authentication: context.get_authentication_key()?,
            spending: context.get_initial_spending_key(network)?,
        })
    }
}

impl Authentication for HardwareSigner {
    fn public_key(&self) -> PublicKey {
        self.authentication
    }

    fn sign(
        &self,
        message: &[u8],
        context: &impl Transactor,
    ) -> Result<Signature, TransactorError> {
        context.sign_message(message)
    }
}

impl Spending for HardwareSigner {
    fn public_key(&self) -> DescriptorPublicKey {
        self.spending.clone()
    }

    fn next(
        &self,
        seen: impl IntoIterator<Item = DescriptorPublicKey>,
        context: &impl Transactor,
    ) -> Result<Self, TransactorError> {
        let network = match self.spending {
            DescriptorPublicKey::XPub(ref xpub) => xpub.xkey.network,
            _ => unimplemented!(),
        };
        let spending = context.get_next_spending_key(Vec::from_iter(seen), network)?;
        Ok(Self { spending, ..*self })
    }

    fn signer(&self, context: &SafeTransactor) -> Arc<dyn TransactionSigner> {
        Arc::new(HardwareBDKSigner::new(&self.spending, context))
    }
}

struct HardwareBDKSigner {
    fingerprint: SignerId,
    transactor: SafeTransactor,
}

impl HardwareBDKSigner {
    fn new(key: &DescriptorPublicKey, transactor: &SafeTransactor) -> Self {
        Self {
            fingerprint: SignerId::from(key.master_fingerprint()),
            transactor: transactor.clone(),
        }
    }
}

impl Debug for HardwareBDKSigner {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("RealHardwareSigner")
            .field("fingerprint", &self.fingerprint)
            .finish()
    }
}

impl SignerCommon for HardwareBDKSigner {
    fn id(&self, _: &Secp256k1<All>) -> SignerId {
        self.fingerprint.clone()
    }

    fn descriptor_secret_key(&self) -> Option<DescriptorSecretKey> {
        None
    }
}

impl TransactionSigner for HardwareBDKSigner {
    fn sign_transaction(
        &self,
        psbt: &mut PartiallySignedTransaction,
        _: &SignOptions,
        _: &Secp256k1<All>,
    ) -> Result<(), SignerError> {
        let SignerId::Fingerprint(fingerprint) = &self.fingerprint else {
            return Err(SignerError::MissingKey);
        };
        let signed = self
            .transactor
            .sign_transaction(psbt.to_owned(), *fingerprint)
            .map_err(|_| SignerError::UserCanceled)?; // TODO: Handle other errors; perhaps enabling the hardware-signer feature of the BDK?
        psbt.combine(signed).expect("psbts didn't combine");
        Ok(())
    }
}
