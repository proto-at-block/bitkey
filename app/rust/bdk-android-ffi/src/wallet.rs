use bdk::bitcoin::blockdata::script::ScriptBuf as BdkScriptBuf;
use bdk::bitcoin::script::PushBytesBuf;
use bdk::bitcoin::{OutPoint as BdkOutPoint, Sequence, Txid};
use bdk::database::any::AnyDatabase;
use bdk::database::{AnyDatabaseConfig, ConfigurableDatabase};
use bdk::wallet::tx_builder::ChangeSpendPolicy;
use bdk::{
    FeeRate, LocalUtxo as BdkLocalUtxo, SignOptions as BdkSignOptions,
    SyncOptions as BdkSyncOptions, Wallet as BdkWallet,
};
use std::collections::HashSet;
use std::convert::TryFrom;
use std::ops::Deref;
use std::str::FromStr;
use std::sync::{Arc, Mutex, MutexGuard};

use crate::blockchain::Blockchain;
use crate::database::DatabaseConfig;
use crate::descriptor::Descriptor;
use crate::psbt::PartiallySignedTransaction;
use crate::Network;
use crate::{
    AddressIndex, AddressInfo, Balance, BdkError, LocalUtxo, OutPoint, Progress, ProgressHolder,
    RbfValue, Script, ScriptAmount, TransactionDetails, TxBuilderResult,
};

#[derive(Debug)]
pub(crate) struct Wallet {
    pub(crate) inner_mutex: Mutex<BdkWallet<AnyDatabase>>,
}

/// A Bitcoin wallet.
/// The Wallet acts as a way of coherently interfacing with output descriptors and related transactions. Its main components are:
///     1. Output descriptors from which it can derive addresses.
///     2. A Database where it tracks transactions and utxos related to the descriptors.
///     3. Signers that can contribute signatures to addresses instantiated from the descriptors.
impl Wallet {
    pub(crate) fn new(
        descriptor: Arc<Descriptor>,
        change_descriptor: Option<Arc<Descriptor>>,
        network: Network,
        database_config: DatabaseConfig,
    ) -> Result<Self, BdkError> {
        let any_database_config = match database_config {
            DatabaseConfig::Memory => AnyDatabaseConfig::Memory(()),
            DatabaseConfig::Sled { config } => AnyDatabaseConfig::Sled(config),
            DatabaseConfig::Sqlite { config } => AnyDatabaseConfig::Sqlite(config),
        };
        let database = AnyDatabase::from_config(&any_database_config)?;
        let descriptor: String = descriptor.as_string_private();
        let change_descriptor: Option<String> = change_descriptor.map(|d| d.as_string_private());

        let wallet_mutex = Mutex::new(BdkWallet::new(
            &descriptor,
            change_descriptor.as_ref(),
            network.into(),
            database,
        )?);
        Ok(Wallet {
            inner_mutex: wallet_mutex,
        })
    }

    pub(crate) fn get_wallet(&self) -> MutexGuard<BdkWallet<AnyDatabase>> {
        self.inner_mutex.lock().expect("wallet")
    }

    /// Get the Bitcoin network the wallet is using.
    pub(crate) fn network(&self) -> Network {
        self.get_wallet().network().into()
    }

    /// Return whether or not a script is part of this wallet (either internal or external).
    pub(crate) fn is_mine(&self, script: Arc<Script>) -> Result<bool, BdkError> {
        self.get_wallet().is_mine(&script.0)
    }

    /// Sync the internal database with the blockchain.
    pub(crate) fn sync(
        &self,
        blockchain: &Blockchain,
        progress: Option<Box<dyn Progress>>,
    ) -> Result<(), BdkError> {
        let bdk_sync_opts = BdkSyncOptions {
            progress: progress.map(|p| {
                Box::new(ProgressHolder { progress: p })
                    as Box<(dyn bdk::blockchain::Progress + 'static)>
            }),
        };

        let blockchain = blockchain.get_blockchain();
        self.get_wallet().sync(blockchain.deref(), bdk_sync_opts)
    }

    /// Return a derived address using the external descriptor, see AddressIndex for available address index selection
    /// strategies. If none of the keys in the descriptor are derivable (i.e. the descriptor does not end with a * character)
    /// then the same address will always be returned for any AddressIndex.
    pub(crate) fn get_address(&self, address_index: AddressIndex) -> Result<AddressInfo, BdkError> {
        self.get_wallet()
            .get_address(address_index.into())
            .map(AddressInfo::from)
    }

    /// Return a derived address using the internal (change) descriptor.
    ///
    /// If the wallet doesn't have an internal descriptor it will use the external descriptor.
    ///
    /// see [`AddressIndex`] for available address index selection strategies. If none of the keys
    /// in the descriptor are derivable (i.e. does not end with /*) then the same address will always
    /// be returned for any [`AddressIndex`].
    pub(crate) fn get_internal_address(
        &self,
        address_index: AddressIndex,
    ) -> Result<AddressInfo, BdkError> {
        self.get_wallet()
            .get_internal_address(address_index.into())
            .map(AddressInfo::from)
    }

    /// Return the balance, meaning the sum of this wallet’s unspent outputs’ values. Note that this method only operates
    /// on the internal database, which first needs to be Wallet.sync manually.
    pub(crate) fn get_balance(&self) -> Result<Balance, BdkError> {
        self.get_wallet().get_balance().map(|b| b.into())
    }

    /// Sign a transaction with all the wallet's signers, in the order specified by every signer's
    /// [`SignerOrdering`]. This function returns the `Result` type with an encapsulated `bool` that
    /// has the value true if the PSBT was finalized, or false otherwise.
    ///
    /// The [`SignOptions`] can be used to tweak the behavior of the software signers, and the way
    /// the transaction is finalized at the end. Note that it can't be guaranteed that *every*
    /// signers will follow the options, but the "software signers" (WIF keys and `xprv`) defined
    /// in this library will.
    pub(crate) fn sign(
        &self,
        psbt: &PartiallySignedTransaction,
        sign_options: Option<SignOptions>,
    ) -> Result<bool, BdkError> {
        let mut psbt = psbt.inner.lock().unwrap();
        self.get_wallet().sign(
            &mut psbt,
            sign_options.map(SignOptions::into).unwrap_or_default(),
        )
    }

    /// Return the list of transactions made and received by the wallet. Note that this method only operate on the internal database, which first needs to be [Wallet.sync] manually.
    pub(crate) fn list_transactions(
        &self,
        include_raw: bool,
    ) -> Result<Vec<TransactionDetails>, BdkError> {
        let transaction_details = self.get_wallet().list_transactions(include_raw)?;
        Ok(transaction_details
            .into_iter()
            .map(TransactionDetails::from)
            .collect())
    }

    /// Return the list of unspent outputs of this wallet. Note that this method only operates on the internal database,
    /// which first needs to be Wallet.sync manually.
    pub(crate) fn list_unspent(&self) -> Result<Vec<LocalUtxo>, BdkError> {
        let unspents: Vec<BdkLocalUtxo> = self.get_wallet().list_unspent()?;
        Ok(unspents.into_iter().map(LocalUtxo::from).collect())
    }
}

/// Options for a software signer
///
/// Adjust the behavior of our software signers and the way a transaction is finalized
#[derive(Debug, Clone, Default)]
pub struct SignOptions {
    /// Whether the signer should trust the `witness_utxo`, if the `non_witness_utxo` hasn't been
    /// provided
    ///
    /// Defaults to `false` to mitigate the "SegWit bug" which should trick the wallet into
    /// paying a fee larger than expected.
    ///
    /// Some wallets, especially if relatively old, might not provide the `non_witness_utxo` for
    /// SegWit transactions in the PSBT they generate: in those cases setting this to `true`
    /// should correctly produce a signature, at the expense of an increased trust in the creator
    /// of the PSBT.
    ///
    /// For more details see: <https://blog.trezor.io/details-of-firmware-updates-for-trezor-one-version-1-9-1-and-trezor-model-t-version-2-3-1-1eba8f60f2dd>
    pub trust_witness_utxo: bool,

    /// Whether the wallet should assume a specific height has been reached when trying to finalize
    /// a transaction
    ///
    /// The wallet will only "use" a timelock to satisfy the spending policy of an input if the
    /// timelock height has already been reached. This option allows overriding the "current height" to let the
    /// wallet use timelocks in the future to spend a coin.
    pub assume_height: Option<u32>,

    /// Whether the signer should use the `sighash_type` set in the PSBT when signing, no matter
    /// what its value is
    ///
    /// Defaults to `false` which will only allow signing using `SIGHASH_ALL`.
    pub allow_all_sighashes: bool,

    /// Whether to remove partial signatures from the PSBT inputs while finalizing PSBT.
    ///
    /// Defaults to `true` which will remove partial signatures during finalization.
    pub remove_partial_sigs: bool,

    /// Whether to try finalizing the PSBT after the inputs are signed.
    ///
    /// Defaults to `true` which will try finalizing PSBT after inputs are signed.
    pub try_finalize: bool,

    // Specifies which Taproot script-spend leaves we should sign for. This option is
    // ignored if we're signing a non-taproot PSBT.
    //
    // Defaults to All, i.e., the wallet will sign all the leaves it has a key for.
    // TODO pub tap_leaves_options: TapLeavesOptions,
    /// Whether we should try to sign a taproot transaction with the taproot internal key
    /// or not. This option is ignored if we're signing a non-taproot PSBT.
    ///
    /// Defaults to `true`, i.e., we always try to sign with the taproot internal key.
    pub sign_with_tap_internal_key: bool,

    /// Whether we should grind ECDSA signature to ensure signing with low r
    /// or not.
    /// Defaults to `true`, i.e., we always grind ECDSA signature to sign with low r.
    pub allow_grinding: bool,
}

impl From<SignOptions> for BdkSignOptions {
    fn from(sign_options: SignOptions) -> Self {
        BdkSignOptions {
            trust_witness_utxo: sign_options.trust_witness_utxo,
            assume_height: sign_options.assume_height,
            allow_all_sighashes: sign_options.allow_all_sighashes,
            remove_partial_sigs: sign_options.remove_partial_sigs,
            try_finalize: sign_options.try_finalize,
            tap_leaves_options: Default::default(),
            sign_with_tap_internal_key: sign_options.sign_with_tap_internal_key,
            allow_grinding: sign_options.allow_grinding,
        }
    }
}

/// A transaction builder.
/// After creating the TxBuilder, you set options on it until finally calling finish to consume the builder and generate the transaction.
/// Each method on the TxBuilder returns an instance of a new TxBuilder with the option set/added.
#[derive(Clone, Debug)]
pub(crate) struct TxBuilder {
    pub(crate) recipients: Vec<(BdkScriptBuf, u64)>,
    pub(crate) utxos: Vec<OutPoint>,
    pub(crate) unspendable: HashSet<OutPoint>,
    pub(crate) change_policy: ChangeSpendPolicy,
    pub(crate) manually_selected_only: bool,
    pub(crate) fee_rate: Option<f32>,
    pub(crate) fee_absolute: Option<u64>,
    pub(crate) drain_wallet: bool,
    pub(crate) drain_to: Option<BdkScriptBuf>,
    pub(crate) rbf: Option<RbfValue>,
    pub(crate) data: Vec<u8>,
}

impl TxBuilder {
    pub(crate) fn new() -> Self {
        TxBuilder {
            recipients: Vec::new(),
            utxos: Vec::new(),
            unspendable: HashSet::new(),
            change_policy: ChangeSpendPolicy::ChangeAllowed,
            manually_selected_only: false,
            fee_rate: None,
            fee_absolute: None,
            drain_wallet: false,
            drain_to: None,
            rbf: None,
            data: Vec::new(),
        }
    }

    /// Add a recipient to the internal list.
    pub(crate) fn add_recipient(&self, script: Arc<Script>, amount: u64) -> Arc<Self> {
        let mut recipients: Vec<(BdkScriptBuf, u64)> = self.recipients.clone();
        recipients.append(&mut vec![(script.0.clone(), amount)]);
        Arc::new(TxBuilder {
            recipients,
            ..self.clone()
        })
    }

    pub(crate) fn set_recipients(&self, recipients: Vec<ScriptAmount>) -> Arc<Self> {
        let recipients = recipients
            .iter()
            .map(|script_amount| (script_amount.script.0.clone(), script_amount.amount))
            .collect();
        Arc::new(TxBuilder {
            recipients,
            ..self.clone()
        })
    }

    /// Add a utxo to the internal list of unspendable utxos. It’s important to note that the "must-be-spent"
    /// utxos added with [TxBuilder.addUtxo] have priority over this. See the Rust docs of the two linked methods for more details.
    pub(crate) fn add_unspendable(&self, unspendable: OutPoint) -> Arc<Self> {
        let mut unspendable_hash_set = self.unspendable.clone();
        unspendable_hash_set.insert(unspendable);
        Arc::new(TxBuilder {
            unspendable: unspendable_hash_set,
            ..self.clone()
        })
    }

    /// Add an outpoint to the internal list of UTXOs that must be spent. These have priority over the "unspendable"
    /// utxos, meaning that if a utxo is present both in the "utxos" and the "unspendable" list, it will be spent.
    pub(crate) fn add_utxo(&self, outpoint: OutPoint) -> Arc<Self> {
        self.add_utxos(vec![outpoint])
    }

    /// Add the list of outpoints to the internal list of UTXOs that must be spent. If an error occurs while adding
    /// any of the UTXOs then none of them are added and the error is returned. These have priority over the "unspendable"
    /// utxos, meaning that if a utxo is present both in the "utxos" and the "unspendable" list, it will be spent.
    pub(crate) fn add_utxos(&self, mut outpoints: Vec<OutPoint>) -> Arc<Self> {
        let mut utxos = self.utxos.to_vec();
        utxos.append(&mut outpoints);
        Arc::new(TxBuilder {
            utxos,
            ..self.clone()
        })
    }

    /// Do not spend change outputs. This effectively adds all the change outputs to the "unspendable" list. See TxBuilder.unspendable.
    pub(crate) fn do_not_spend_change(&self) -> Arc<Self> {
        Arc::new(TxBuilder {
            change_policy: ChangeSpendPolicy::ChangeForbidden,
            ..self.clone()
        })
    }

    /// Only spend utxos added by [add_utxo]. The wallet will not add additional utxos to the transaction even if they are
    /// needed to make the transaction valid.
    pub(crate) fn manually_selected_only(&self) -> Arc<Self> {
        Arc::new(TxBuilder {
            manually_selected_only: true,
            ..self.clone()
        })
    }

    /// Only spend change outputs. This effectively adds all the non-change outputs to the "unspendable" list. See TxBuilder.unspendable.
    pub(crate) fn only_spend_change(&self) -> Arc<Self> {
        Arc::new(TxBuilder {
            change_policy: ChangeSpendPolicy::OnlyChange,
            ..self.clone()
        })
    }

    /// Replace the internal list of unspendable utxos with a new list. It’s important to note that the "must-be-spent" utxos added with
    /// TxBuilder.addUtxo have priority over these. See the Rust docs of the two linked methods for more details.
    pub(crate) fn unspendable(&self, unspendable: Vec<OutPoint>) -> Arc<Self> {
        Arc::new(TxBuilder {
            unspendable: unspendable.into_iter().collect(),
            ..self.clone()
        })
    }

    /// Set a custom fee rate.
    pub(crate) fn fee_rate(&self, sat_per_vb: f32) -> Arc<Self> {
        Arc::new(TxBuilder {
            fee_rate: Some(sat_per_vb),
            ..self.clone()
        })
    }

    /// Set an absolute fee.
    pub(crate) fn fee_absolute(&self, fee_amount: u64) -> Arc<Self> {
        Arc::new(TxBuilder {
            fee_absolute: Some(fee_amount),
            ..self.clone()
        })
    }

    /// Spend all the available inputs. This respects filters like TxBuilder.unspendable and the change policy.
    pub(crate) fn drain_wallet(&self) -> Arc<Self> {
        Arc::new(TxBuilder {
            drain_wallet: true,
            ..self.clone()
        })
    }

    /// Sets the address to drain excess coins to. Usually, when there are excess coins they are sent to a change address
    /// generated by the wallet. This option replaces the usual change address with an arbitrary ScriptPubKey of your choosing.
    /// Just as with a change output, if the drain output is not needed (the excess coins are too small) it will not be included
    /// in the resulting transaction. The only difference is that it is valid to use drain_to without setting any ordinary recipients
    /// with add_recipient (but it is perfectly fine to add recipients as well). If you choose not to set any recipients, you should
    /// either provide the utxos that the transaction should spend via add_utxos, or set drain_wallet to spend all of them.
    /// When bumping the fees of a transaction made with this option, you probably want to use BumpFeeTxBuilder.allow_shrinking
    /// to allow this output to be reduced to pay for the extra fees.
    pub(crate) fn drain_to(&self, script: Arc<Script>) -> Arc<Self> {
        Arc::new(TxBuilder {
            drain_to: Some(script.0.clone()),
            ..self.clone()
        })
    }

    /// Enable signaling RBF. This will use the default `nsequence` value of `0xFFFFFFFD`.
    pub(crate) fn enable_rbf(&self) -> Arc<Self> {
        Arc::new(TxBuilder {
            rbf: Some(RbfValue::Default),
            ..self.clone()
        })
    }

    /// Enable signaling RBF with a specific nSequence value. This can cause conflicts if the wallet's descriptors contain an
    /// "older" (OP_CSV) operator and the given `nsequence` is lower than the CSV value. If the `nsequence` is higher than `0xFFFFFFFD`
    /// an error will be thrown, since it would not be a valid nSequence to signal RBF.
    pub(crate) fn enable_rbf_with_sequence(&self, nsequence: u32) -> Arc<Self> {
        Arc::new(TxBuilder {
            rbf: Some(RbfValue::Value(nsequence)),
            ..self.clone()
        })
    }

    /// Add data as an output using OP_RETURN.
    pub(crate) fn add_data(&self, data: Vec<u8>) -> Arc<Self> {
        Arc::new(TxBuilder {
            data,
            ..self.clone()
        })
    }

    /// Finish building the transaction. Returns the BIP174 PSBT.
    pub(crate) fn finish(&self, wallet: &Wallet) -> Result<TxBuilderResult, BdkError> {
        let wallet = wallet.get_wallet();
        let mut tx_builder = wallet.build_tx();
        for (script, amount) in &self.recipients {
            tx_builder.add_recipient(script.clone(), *amount);
        }
        tx_builder.change_policy(self.change_policy);
        if !self.utxos.is_empty() {
            let bdk_utxos: Vec<BdkOutPoint> = self.utxos.iter().map(BdkOutPoint::from).collect();
            let utxos: &[BdkOutPoint] = &bdk_utxos;
            tx_builder.add_utxos(utxos)?;
        }
        if !self.unspendable.is_empty() {
            let bdk_unspendable: Vec<BdkOutPoint> =
                self.unspendable.iter().map(BdkOutPoint::from).collect();
            tx_builder.unspendable(bdk_unspendable);
        }
        if self.manually_selected_only {
            tx_builder.manually_selected_only();
        }
        if let Some(sat_per_vb) = self.fee_rate {
            tx_builder.fee_rate(FeeRate::from_sat_per_vb(sat_per_vb));
        }
        if let Some(fee_amount) = self.fee_absolute {
            tx_builder.fee_absolute(fee_amount);
        }
        if self.drain_wallet {
            tx_builder.drain_wallet();
        }
        if let Some(script) = &self.drain_to {
            tx_builder.drain_to(script.clone());
        }
        if let Some(rbf) = &self.rbf {
            match *rbf {
                RbfValue::Default => {
                    tx_builder.enable_rbf();
                }
                RbfValue::Value(nsequence) => {
                    tx_builder.enable_rbf_with_sequence(Sequence(nsequence));
                }
            }
        }
        if !&self.data.is_empty() {
            let push_bytes = PushBytesBuf::try_from(self.data.clone()).map_err(|_| {
                BdkError::Generic("Failed to convert data to PushBytes".to_string())
            })?;
            tx_builder.add_data(&push_bytes);
        }

        tx_builder
            .finish()
            .map(|(psbt, tx_details)| TxBuilderResult {
                psbt: Arc::new(PartiallySignedTransaction {
                    inner: Mutex::new(psbt),
                }),
                transaction_details: TransactionDetails::from(tx_details),
            })
    }
}

/// The BumpFeeTxBuilder is used to bump the fee on a transaction that has been broadcast and has its RBF flag set to true.
#[derive(Clone)]
pub(crate) struct BumpFeeTxBuilder {
    pub(crate) txid: String,
    pub(crate) fee_rate: f32,
    pub(crate) allow_shrinking: Option<Arc<Script>>,
    pub(crate) rbf: Option<RbfValue>,
}

impl BumpFeeTxBuilder {
    pub(crate) fn new(txid: String, fee_rate: f32) -> Self {
        Self {
            txid,
            fee_rate,
            allow_shrinking: None,
            rbf: None,
        }
    }

    /// Explicitly tells the wallet that it is allowed to reduce the amount of the output matching this script_pubkey
    /// in order to bump the transaction fee. Without specifying this the wallet will attempt to find a change output to
    /// shrink instead. Note that the output may shrink to below the dust limit and therefore be removed. If it is preserved
    /// then it is currently not guaranteed to be in the same position as it was originally. Returns an error if script_pubkey
    /// can’t be found among the recipients of the transaction we are bumping.
    pub(crate) fn allow_shrinking(&self, script_pubkey: Arc<Script>) -> Arc<Self> {
        Arc::new(Self {
            allow_shrinking: Some(script_pubkey),
            ..self.clone()
        })
    }

    /// Enable signaling RBF. This will use the default `nsequence` value of `0xFFFFFFFD`.
    pub(crate) fn enable_rbf(&self) -> Arc<Self> {
        Arc::new(Self {
            rbf: Some(RbfValue::Default),
            ..self.clone()
        })
    }

    /// Enable signaling RBF with a specific nSequence value. This can cause conflicts if the wallet's descriptors contain an
    /// "older" (OP_CSV) operator and the given `nsequence` is lower than the CSV value. If the `nsequence` is higher than `0xFFFFFFFD`
    /// an error will be thrown, since it would not be a valid nSequence to signal RBF.
    pub(crate) fn enable_rbf_with_sequence(&self, nsequence: u32) -> Arc<Self> {
        Arc::new(Self {
            rbf: Some(RbfValue::Value(nsequence)),
            ..self.clone()
        })
    }

    /// Finish building the transaction. Returns the BIP174 PSBT.
    pub(crate) fn finish(
        &self,
        wallet: &Wallet,
    ) -> Result<Arc<PartiallySignedTransaction>, BdkError> {
        let wallet = wallet.get_wallet();
        let txid = Txid::from_str(self.txid.as_str())?;
        let mut tx_builder = wallet.build_fee_bump(txid)?;
        tx_builder.fee_rate(FeeRate::from_sat_per_vb(self.fee_rate));
        if let Some(allow_shrinking) = &self.allow_shrinking {
            tx_builder.allow_shrinking(allow_shrinking.0.clone())?;
        }
        if let Some(rbf) = &self.rbf {
            match *rbf {
                RbfValue::Default => {
                    tx_builder.enable_rbf();
                }
                RbfValue::Value(nsequence) => {
                    tx_builder.enable_rbf_with_sequence(Sequence(nsequence));
                }
            }
        }
        tx_builder
            .finish()
            .map(|(psbt, _)| PartiallySignedTransaction {
                inner: Mutex::new(psbt),
            })
            .map(Arc::new)
    }
}

// The goal of these tests to to ensure `bdk-ffi` intermediate code correctly calls `bdk` APIs.
// These tests should not be used to verify `bdk` behavior that is already tested in the `bdk`
// crate.
#[cfg(test)]
mod test {
    use crate::database::DatabaseConfig;
    use crate::descriptor::Descriptor;
    use crate::keys::{DescriptorSecretKey, Mnemonic};
    use crate::wallet::{AddressIndex, TxBuilder, Wallet};
    use crate::Network;
    use crate::Script;
    use assert_matches::assert_matches;
    use bdk::bitcoin::Address;
    use bdk::wallet::get_funded_wallet;
    use bdk::KeychainKind;
    use std::str::FromStr;
    use std::sync::{Arc, Mutex};

    #[test]
    fn test_drain_wallet() {
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
            .drain_wallet()
            .drain_to(drain_to_script.clone());
        assert!(tx_builder.drain_wallet);
        assert_eq!(tx_builder.drain_to, Some(drain_to_script.0.clone()));

        let tx_builder_result = tx_builder.finish(&test_wallet).unwrap();
        let psbt = tx_builder_result.psbt.inner.lock().unwrap().clone();
        let tx_details = tx_builder_result.transaction_details;

        // confirm one input with 50,000 sats
        assert_eq!(psbt.inputs.len(), 1);
        let input_value = psbt
            .inputs
            .first()
            .cloned()
            .unwrap()
            .non_witness_utxo
            .unwrap()
            .output
            .first()
            .unwrap()
            .value;
        assert_eq!(input_value, 50_000_u64);

        // confirm one output to correct address with all sats - fee
        assert_eq!(psbt.outputs.len(), 1);
        let output_address = Address::from_script(
            &psbt.unsigned_tx.output.first().unwrap().script_pubkey,
            Network::Testnet.into(),
        )
        .unwrap();
        assert_eq!(
            output_address,
            Address::from_str("tb1ql7w62elx9ucw4pj5lgw4l028hmuw80sndtntxt")
                .unwrap()
                .assume_checked()
        );
        let output_value = psbt.unsigned_tx.output.first().cloned().unwrap().value;
        assert_eq!(output_value, 49_890_u64); // input - fee

        assert_eq!(
            tx_details.txid,
            "312f1733badab22dc26b8dcbc83ba5629fb7b493af802e8abe07d865e49629c5"
        );
        assert_eq!(tx_details.received, 0);
        assert_eq!(tx_details.sent, 50000);
        assert!(tx_details.fee.is_some());
        assert_eq!(tx_details.fee.unwrap(), 110);
        assert!(tx_details.confirmation_time.is_none());
    }

    #[test]
    fn test_peek_reset_address() {
        let test_wpkh = "wpkh(tprv8hwWMmPE4BVNxGdVt3HhEERZhondQvodUY7Ajyseyhudr4WabJqWKWLr4Wi2r26CDaNCQhhxEftEaNzz7dPGhWuKFU4VULesmhEfZYyBXdE/0/*)";
        let descriptor = Descriptor::new(test_wpkh.to_string(), Network::Regtest).unwrap();
        let change_descriptor = Descriptor::new(
            test_wpkh.to_string().replace("/0/*", "/1/*"),
            Network::Regtest,
        )
        .unwrap();

        let wallet = Wallet::new(
            Arc::new(descriptor),
            Some(Arc::new(change_descriptor)),
            Network::Regtest,
            DatabaseConfig::Memory,
        )
        .unwrap();

        assert_eq!(
            wallet
                .get_address(AddressIndex::Peek { index: 2 })
                .unwrap()
                .address
                .as_string(),
            "bcrt1q5g0mq6dkmwzvxscqwgc932jhgcxuqqkjv09tkj"
        );

        assert_eq!(
            wallet
                .get_address(AddressIndex::Peek { index: 1 })
                .unwrap()
                .address
                .as_string(),
            "bcrt1q0xs7dau8af22rspp4klya4f7lhggcnqfun2y3a"
        );

        // new index still 0
        assert_eq!(
            wallet
                .get_address(AddressIndex::New)
                .unwrap()
                .address
                .as_string(),
            "bcrt1qqjn9gky9mkrm3c28e5e87t5akd3twg6xezp0tv"
        );

        // new index now 1
        assert_eq!(
            wallet
                .get_address(AddressIndex::New)
                .unwrap()
                .address
                .as_string(),
            "bcrt1q0xs7dau8af22rspp4klya4f7lhggcnqfun2y3a"
        );

        // new index now 2
        assert_eq!(
            wallet
                .get_address(AddressIndex::New)
                .unwrap()
                .address
                .as_string(),
            "bcrt1q5g0mq6dkmwzvxscqwgc932jhgcxuqqkjv09tkj"
        );

        // peek index 1
        assert_eq!(
            wallet
                .get_address(AddressIndex::Peek { index: 1 })
                .unwrap()
                .address
                .as_string(),
            "bcrt1q0xs7dau8af22rspp4klya4f7lhggcnqfun2y3a"
        );

        // reset to index 0
        assert_eq!(
            wallet
                .get_address(AddressIndex::Reset { index: 0 })
                .unwrap()
                .address
                .as_string(),
            "bcrt1qqjn9gky9mkrm3c28e5e87t5akd3twg6xezp0tv"
        );

        // new index 1 again
        assert_eq!(
            wallet
                .get_address(AddressIndex::New)
                .unwrap()
                .address
                .as_string(),
            "bcrt1q0xs7dau8af22rspp4klya4f7lhggcnqfun2y3a"
        );
    }

    #[test]
    fn test_get_address() {
        let test_wpkh = "wpkh(tprv8hwWMmPE4BVNxGdVt3HhEERZhondQvodUY7Ajyseyhudr4WabJqWKWLr4Wi2r26CDaNCQhhxEftEaNzz7dPGhWuKFU4VULesmhEfZYyBXdE/0/*)";
        let descriptor = Descriptor::new(test_wpkh.to_string(), Network::Regtest).unwrap();
        let change_descriptor = Descriptor::new(
            test_wpkh.to_string().replace("/0/*", "/1/*"),
            Network::Regtest,
        )
        .unwrap();

        let wallet = Wallet::new(
            Arc::new(descriptor),
            Some(Arc::new(change_descriptor)),
            Network::Regtest,
            DatabaseConfig::Memory,
        )
        .unwrap();

        assert_eq!(
            wallet
                .get_address(AddressIndex::New)
                .unwrap()
                .address
                .as_string(),
            "bcrt1qqjn9gky9mkrm3c28e5e87t5akd3twg6xezp0tv"
        );

        assert_eq!(
            wallet
                .get_address(AddressIndex::New)
                .unwrap()
                .address
                .as_string(),
            "bcrt1q0xs7dau8af22rspp4klya4f7lhggcnqfun2y3a"
        );

        assert_eq!(
            wallet
                .get_address(AddressIndex::LastUnused)
                .unwrap()
                .address
                .as_string(),
            "bcrt1q0xs7dau8af22rspp4klya4f7lhggcnqfun2y3a"
        );

        assert_eq!(
            wallet
                .get_internal_address(AddressIndex::New)
                .unwrap()
                .address
                .as_string(),
            "bcrt1qpmz73cyx00r4a5dea469j40ax6d6kqyd67nnpj"
        );

        assert_eq!(
            wallet
                .get_internal_address(AddressIndex::New)
                .unwrap()
                .address
                .as_string(),
            "bcrt1qaux734vuhykww9632v8cmdnk7z2mw5lsf74v6k"
        );

        assert_eq!(
            wallet
                .get_internal_address(AddressIndex::LastUnused)
                .unwrap()
                .address
                .as_string(),
            "bcrt1qaux734vuhykww9632v8cmdnk7z2mw5lsf74v6k"
        );
    }

    #[test]
    fn test_is_mine() {
        // is_mine should return true for addresses generated by the wallet
        let mnemonic: Mnemonic = Mnemonic::from_string("chaos fabric time speed sponsor all flat solution wisdom trophy crack object robot pave observe combine where aware bench orient secret primary cable detect".to_string()).unwrap();
        let secret_key: DescriptorSecretKey =
            DescriptorSecretKey::new(Network::Testnet, Arc::new(mnemonic), None);
        let descriptor: Descriptor = Descriptor::new_bip84(
            Arc::new(secret_key),
            KeychainKind::External,
            Network::Testnet,
        );
        let wallet: Wallet = Wallet::new(
            Arc::new(descriptor),
            None,
            Network::Testnet,
            DatabaseConfig::Memory,
        )
        .unwrap();

        let address = wallet.get_address(AddressIndex::New).unwrap();
        let script: Arc<Script> = address.address.script_pubkey();

        let is_mine_1: bool = wallet.is_mine(script).unwrap();
        assert!(is_mine_1);

        // is_mine returns false when provided a script that is not in the wallet
        let other_wpkh = "wpkh(tprv8hwWMmPE4BVNxGdVt3HhEERZhondQvodUY7Ajyseyhudr4WabJqWKWLr4Wi2r26CDaNCQhhxEftEaNzz7dPGhWuKFU4VULesmhEfZYyBXdE/0/*)";
        let other_descriptor = Descriptor::new(other_wpkh.to_string(), Network::Testnet).unwrap();

        let other_wallet = Wallet::new(
            Arc::new(other_descriptor),
            None,
            Network::Testnet,
            DatabaseConfig::Memory,
        )
        .unwrap();

        let other_address = other_wallet.get_address(AddressIndex::New).unwrap();
        let other_script: Arc<Script> = other_address.address.script_pubkey();
        let is_mine_2: bool = wallet.is_mine(other_script).unwrap();
        assert_matches!(is_mine_2, false);
    }
}
