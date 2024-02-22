pub(crate) mod hardware;
pub(crate) mod seed;

use std::sync::Arc;

use bdk::{
    bitcoin::secp256k1::{ecdsa::Signature, PublicKey},
    miniscript::DescriptorPublicKey,
    signer::TransactionSigner,
};
use wca::pcsc::{Transactor, TransactorError};

use crate::nfc::SafeTransactor;

pub(crate) trait Authentication: Sized {
    fn public_key(&self) -> PublicKey;
    fn sign(&self, message: &[u8], context: &impl Transactor)
        -> Result<Signature, TransactorError>;
}

pub(crate) trait Spending: Sized {
    fn public_key(&self) -> DescriptorPublicKey;
    fn next(
        &self,
        seen: impl Iterator<Item = DescriptorPublicKey>,
        context: &impl Transactor,
    ) -> Result<Self, TransactorError>;
    fn signer(&self, context: &SafeTransactor) -> Arc<dyn TransactionSigner>;
}
