use anyhow::Result;
use bdk::{
    bitcoin::bip32::ChildNumber,
    descriptor::{DescriptorPublicKey, ExtendedDescriptor},
    miniscript::Descriptor,
    signer::SignerOrdering,
    Wallet,
};
use sled::{Db, Tree};
use wca::{pcsc::NullTransactor, signing::ExtendDerivationPath};

use crate::{
    entities::{Account, DescriptorKeyset, Keyset, SignerPair},
    nfc::SafeTransactor,
    signers::Spending,
};

use super::DB_WALLET;

impl SignerPair {
    pub(crate) fn wallet(
        &self,
        account: &Account,
        db: &Db,
        context: Option<&SafeTransactor>,
    ) -> Result<Wallet<Tree>> {
        let keyset = find_active_keyset(&account.keysets, self);
        let receive_descriptor = keyset.receiving().into_multisig_descriptor();
        let change_descriptor = keyset.change().into_multisig_descriptor();

        let mut wallet = Wallet::new(
            receive_descriptor,
            Some(change_descriptor),
            self.network,
            db.open_tree(DB_WALLET)?,
        )?;

        let signer_application = self
            .application
            .signer(&SafeTransactor::new(NullTransactor));
        wallet.add_signer(
            bdk::KeychainKind::External,
            SignerOrdering::default(),
            signer_application.clone(),
        );

        if let Some(context) = context {
            let signer_hardware = self.hardware.signer(context);
            wallet.add_signer(
                bdk::KeychainKind::External,
                SignerOrdering::default(),
                signer_hardware.clone(),
            );
        }

        Ok(wallet)
    }
}

pub const SPENDING_PATH: [ChildNumber; 1] = [ChildNumber::Normal { index: 0 }];
pub const CHANGE_PATH: [ChildNumber; 1] = [ChildNumber::Normal { index: 1 }];

fn find_active_keyset(keysets: &Vec<Keyset>, pair: &SignerPair) -> DescriptorKeyset {
    let application_pk = pair.application.public_key();
    let hardware_pk = pair.hardware.public_key();
    let server_pk = keysets
        .iter()
        .find(|ks| ks.keys.application == application_pk && ks.keys.hardware == hardware_pk)
        .expect("paired hardware not found in account keyset")
        .keys
        .server
        .clone();

    DescriptorKeyset {
        application: application_pk,
        hardware: hardware_pk,
        server: server_pk,
    }
}

impl DescriptorKeyset {
    pub fn receiving(&self) -> DescriptorKeyset {
        self.derive(&SPENDING_PATH)
    }

    pub fn change(&self) -> DescriptorKeyset {
        self.derive(&CHANGE_PATH)
    }

    fn derive(&self, path: &[ChildNumber]) -> DescriptorKeyset {
        Self {
            application: self.application.extend_derivation_path(path),
            hardware: self.hardware.extend_derivation_path(path),
            server: self.server.extend_derivation_path(path),
        }
    }

    pub fn into_multisig_descriptor(self) -> ExtendedDescriptor {
        let application_dpub = self.application;
        let hardware_dpub = self.hardware;
        let server_dpub = self.server;

        Descriptor::<DescriptorPublicKey>::new_wsh_sortedmulti(
            2,
            vec![application_dpub, hardware_dpub, server_dpub],
        )
        .expect("could not create descriptor")
    }
}
