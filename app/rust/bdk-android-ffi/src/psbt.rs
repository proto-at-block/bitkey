use bdk::bitcoin::psbt::PartiallySignedTransaction as BdkPartiallySignedTransaction;
use bdk::bitcoincore_rpc::jsonrpc::serde_json;
use bdk::psbt::PsbtUtils;
use std::ops::Deref;
use std::str::FromStr;
use std::sync::{Arc, Mutex};

use crate::{BdkError, FeeRate, Transaction};

#[derive(Debug)]
pub(crate) struct PartiallySignedTransaction {
    pub(crate) inner: Mutex<BdkPartiallySignedTransaction>,
}

impl PartiallySignedTransaction {
    pub(crate) fn new(psbt_base64: String) -> Result<Self, BdkError> {
        let psbt: BdkPartiallySignedTransaction =
            BdkPartiallySignedTransaction::from_str(&psbt_base64)?;
        Ok(PartiallySignedTransaction {
            inner: Mutex::new(psbt),
        })
    }

    pub(crate) fn serialize(&self) -> String {
        let psbt = self.inner.lock().unwrap().clone();
        psbt.to_string()
    }

    pub(crate) fn txid(&self) -> String {
        let tx = self.inner.lock().unwrap().clone().extract_tx();
        let txid = tx.txid();
        txid.to_string()
    }

    /// Return the transaction.
    pub(crate) fn extract_tx(&self) -> Arc<Transaction> {
        let tx = self.inner.lock().unwrap().clone().extract_tx();
        Arc::new(tx.into())
    }

    /// Combines this PartiallySignedTransaction with other PSBT as described by BIP 174.
    ///
    /// In accordance with BIP 174 this function is commutative i.e., `A.combine(B) == B.combine(A)`
    pub(crate) fn combine(
        &self,
        other: Arc<PartiallySignedTransaction>,
    ) -> Result<Arc<PartiallySignedTransaction>, BdkError> {
        let other_psbt = other.inner.lock().unwrap().clone();
        let mut original_psbt = self.inner.lock().unwrap().clone();

        original_psbt.combine(other_psbt)?;
        Ok(Arc::new(PartiallySignedTransaction {
            inner: Mutex::new(original_psbt),
        }))
    }

    /// The total transaction fee amount, sum of input amounts minus sum of output amounts, in sats.
    /// If the PSBT is missing a TxOut for an input returns None.
    pub(crate) fn fee_amount(&self) -> Option<u64> {
        self.inner.lock().unwrap().fee_amount()
    }

    /// The transaction's fee rate. This value will only be accurate if calculated AFTER the
    /// `PartiallySignedTransaction` is finalized and all witness/signature data is added to the
    /// transaction.
    /// If the PSBT is missing a TxOut for an input returns None.
    pub(crate) fn fee_rate(&self) -> Option<Arc<FeeRate>> {
        self.inner.lock().unwrap().fee_rate().map(Arc::new)
    }

    /// Serialize the PSBT data structure as a String of JSON.
    pub(crate) fn json_serialize(&self) -> String {
        let psbt = self.inner.lock().unwrap();
        serde_json::to_string(psbt.deref()).unwrap()
    }
}

// The goal of these tests to to ensure `bdk-ffi` intermediate code correctly calls `bdk` APIs.
// These tests should not be used to verify `bdk` behavior that is already tested in the `bdk`
// crate.
#[cfg(test)]
mod test {
    use crate::wallet::{TxBuilder, Wallet};
    use crate::Network;
    use bdk::wallet::get_funded_wallet;
    use std::sync::Mutex;

    #[test]
    fn test_psbt_fee() {
        let test_wpkh = "wpkh(cVpPVruEDdmutPzisEsYvtST1usBR3ntr8pXSyt6D2YYqXRyPcFW)";
        let (funded_wallet, _, _) = get_funded_wallet(test_wpkh);
        let test_wallet = Wallet {
            inner_mutex: Mutex::new(funded_wallet),
        };
        let drain_to_address = "tb1ql7w62elx9ucw4pj5lgw4l028hmuw80sndtntxt".to_string();
        let drain_to_script = crate::Address::new(drain_to_address, Network::Testnet)
            .unwrap()
            .script_pubkey();

        let tx_builder = TxBuilder::new()
            .fee_rate(2.0)
            .drain_wallet()
            .drain_to(drain_to_script.clone());
        //dbg!(&tx_builder);
        assert!(tx_builder.drain_wallet);
        assert_eq!(tx_builder.drain_to, Some(drain_to_script.0.clone()));

        let tx_builder_result = tx_builder.finish(&test_wallet).unwrap();

        assert!(tx_builder_result.psbt.fee_rate().is_some());
        assert_eq!(
            tx_builder_result.psbt.fee_rate().unwrap().as_sat_per_vb(),
            2.682927
        );

        assert!(tx_builder_result.psbt.fee_amount().is_some());
        assert_eq!(tx_builder_result.psbt.fee_amount().unwrap(), 220);
    }
}
