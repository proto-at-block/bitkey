package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkIO
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressInfo
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.bdk.*
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.*
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.catchingResult
import build.wallet.coroutines.flow.launchTicker
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.money.BitcoinMoney
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import uniffi.bdk.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import uniffi.bdk.Address as BdkV2Address
import uniffi.bdk.Psbt as BdkV2Psbt
import uniffi.bdk.Script as BdkV2Script
import uniffi.bdk.Wallet as BdkV2Wallet

class SpendingWalletV2Impl(
  override val identifier: String,
  override val networkType: BitcoinNetworkType,
  private val bdkWallet: BdkV2Wallet,
  private val persister: Persister,
  private val appSessionManager: AppSessionManager,
  private val bdkTransactionMapperV2: BdkTransactionMapperV2,
  private val bdkWalletSyncerV2: BdkWalletSyncerV2,
  private val syncContext: CoroutineContext = Dispatchers.IO,
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
) : SpendingWallet {
  private val balanceState = MutableStateFlow<BitcoinBalance?>(null)
  private val transactionsState = MutableStateFlow<List<BitcoinTransaction>?>(null)
  private val unspentOutputsState = MutableStateFlow<List<BdkUtxo>?>(null)

  override suspend fun initializeBalanceAndTransactions() {
    getBalance().onSuccess { balanceState.value = it }
    getTransactions().onSuccess { transactionsState.value = it }
    getUnspentOutputs().onSuccess { unspentOutputsState.value = it }
  }

  override suspend fun sync(): Result<Unit, Error> {
    return withContext(syncContext) {
      coroutineBinding {
        bdkWalletSyncerV2.sync(
          bdkWallet = bdkWallet,
          persister = persister,
          networkType = networkType
        )
          .mapError { SpendingWalletV2Error.SyncFailed(it) }
          .bind()

        getTransactions()
          .bind()
          .also { transactionsState.value = it }

        getBalance()
          .bind()
          .also { balanceState.value = it }

        getUnspentOutputs()
          .bind()
          .also { unspentOutputsState.value = it }
      }
    }
  }

  override fun launchPeriodicSync(
    scope: CoroutineScope,
    interval: Duration,
  ): Job {
    return scope.launchTicker(interval, syncContext) {
      if (appSessionManager.isAppForegrounded()) {
        sync()
      }
    }
  }

  override suspend fun getNewAddress(): Result<BitcoinAddress, Error> {
    return catchingResult {
      val addressInfo = bdkWallet.revealNextAddress(KeychainKind.EXTERNAL)
      bdkWallet.persist(persister)
      BitcoinAddress(addressInfo.address.toString())
    }.mapError { SpendingWalletV2Error.AddressGenerationFailed(it) }
      .logFailure { "BDK2 address retrieval failed (operation=new)" }
  }

  override suspend fun getNewAddressInfo(): Result<BitcoinAddressInfo, Error> {
    return catchingResult {
      val addressInfo = bdkWallet.revealNextAddress(KeychainKind.EXTERNAL)
      bdkWallet.persist(persister)
      BitcoinAddressInfo(
        address = BitcoinAddress(addressInfo.address.toString()),
        index = addressInfo.index
      )
    }.mapError { SpendingWalletV2Error.AddressGenerationFailed(it) }
      .logFailure { "BDK2 address retrieval failed (operation=new_with_index)" }
  }

  override suspend fun peekAddress(index: UInt): Result<BitcoinAddress, Error> {
    return catchingResult {
      val addressInfo = bdkWallet.peekAddress(KeychainKind.EXTERNAL, index)
      BitcoinAddress(addressInfo.address.toString())
    }.mapError { SpendingWalletV2Error.AddressPeekFailed(index, it) }
      .logFailure { "BDK2 address retrieval failed (operation=peek)" }
  }

  override suspend fun revealAddress(index: UInt): Result<BitcoinAddress, Error> {
    return catchingResult {
      val newlyRevealed = bdkWallet.revealAddressesTo(KeychainKind.EXTERNAL, index)
      if (newlyRevealed.isNotEmpty()) {
        bdkWallet.persist(persister)
      }
      val addressInfo = bdkWallet.peekAddress(KeychainKind.EXTERNAL, index)
      BitcoinAddress(addressInfo.address.toString())
    }.mapError { SpendingWalletV2Error.AddressRevealFailed(index, it) }
      .logFailure { "BDK2 address retrieval failed (operation=reveal)" }
  }

  // TODO: rename this to nextUnused when we remove legacy bdk impl
  override suspend fun getLastUnusedAddress(): Result<BitcoinAddress, Error> {
    return catchingResult {
      val addressInfo = bdkWallet.nextUnusedAddress(KeychainKind.EXTERNAL)
      bdkWallet.persist(persister)
      BitcoinAddress(addressInfo.address.toString())
    }.mapError { SpendingWalletV2Error.LastUnusedAddressFailed(it) }
      .logFailure { "BDK2 address retrieval failed (operation=last_unused)" }
  }

  override suspend fun isMine(address: BitcoinAddress): Result<Boolean, Error> {
    return catchingResult {
      val bdkAddress = BdkV2Address(address.address, networkType.bdkNetworkV2)
      bdkWallet.isMine(bdkAddress.scriptPubkey())
    }.mapError { SpendingWalletV2Error.IsMineCheckFailed(it) }
  }

  override suspend fun isMine(scriptPubKey: BdkScript): Result<Boolean, Error> {
    return catchingResult {
      val script = BdkV2Script(scriptPubKey.rawOutputScript.toUByteArray().toByteArray())
      bdkWallet.isMine(script)
    }.mapError { SpendingWalletV2Error.IsMineCheckFailed(it) }
  }

  override fun balance(): Flow<BitcoinBalance> = balanceState.filterNotNull()

  override fun transactions(): Flow<List<BitcoinTransaction>> = transactionsState.filterNotNull()

  override fun unspentOutputs(): Flow<List<BdkUtxo>> = unspentOutputsState.filterNotNull()

  // TODO(W-15850): Migrate callers to use createSignedPsbt() instead.
  //  BitcoinTransactionFeeEstimatorImpl and isBalanceSpendable() still use this legacy API.
  //  Blocks all send operations when BDK2 is enabled.
  override suspend fun createPsbt(
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
    feePolicy: FeePolicy,
    coinSelectionStrategy: CoinSelectionStrategy,
  ): Result<Psbt, Throwable> =
    withContext(Dispatchers.BdkIO) {
      validateFeePolicy(feePolicy)?.let { return@withContext Err(it) }

      val bdkAddress = catchingResult {
        BdkV2Address(recipientAddress.address, networkType.bdkNetworkV2)
      }.getOrElse {
        return@withContext Err(it.toBdkError())
      }

      val bdkPsbt = catchingResult {
        TxBuilder()
          .feePolicy(feePolicy)
          .sendTo(bdkAddress.scriptPubkey(), amount)
          .coinSelectionStrategy(coinSelectionStrategy)
          .setExactSequence(RBF_SEQUENCE)
          .finish(bdkWallet)
      }.getOrElse {
        return@withContext Err(it.toBdkError())
      }

      catchingResult {
        bdkWallet.persist(persister)
      }.getOrElse {
        return@withContext Err(SpendingWalletV2Error.PersistFailed(it))
      }

      catchingResult {
        bdkPsbt.toPsbt()
      }.getOrElse {
        return@withContext Err(it.toPsbtConversionError())
      }.let { Ok(it) }
    }

  override suspend fun signPsbt(psbt: Psbt): Result<Psbt, Throwable> =
    withContext(Dispatchers.BdkIO) {
      val bdkPsbt = catchingResult {
        BdkV2Psbt(psbt.base64)
      }.getOrElse {
        return@withContext Err(SpendingWalletV2Error.PsbtSigningFailed(it))
      }

      catchingResult {
        bdkWallet.sign(bdkPsbt)
      }.getOrElse {
        return@withContext Err(SpendingWalletV2Error.PsbtSigningFailed(it))
      }

      // Preserve amountSats and fee from the input PSBT since those don't change during signing.
      // Only update base64 (with signatures), id (txid changes with signatures), and vsize
      // (can change for non-SegWit inputs where signatures go in scriptSig, not witness).
      // We intentionally do NOT call toPsbt() here because toPsbt() calls isMine() to calculate
      // amountSats, and this wallet may have different keys than the wallet that created the PSBT
      // (e.g., fake hardware wallet for testing).
      catchingResult {
        val tx = bdkPsbt.extractTx()
        psbt.copy(
          id = tx.computeTxid().toString(),
          base64 = bdkPsbt.serialize(),
          vsize = tx.vsize().toLong(),
          inputs = tx.input().map { it.toBdkTxIn() }.toSet(),
          outputs = tx.output().map { it.toBdkTxOut() }.toSet(),
          numOfInputs = tx.input().size
        )
      }.getOrElse {
        return@withContext Err(SpendingWalletV2Error.PsbtSigningFailed(it))
      }.let { Ok(it) }
    }.logFailure { "BDK2 PSBT signing failed" }

  override suspend fun createSignedPsbt(
    constructionType: PsbtConstructionMethod,
  ): Result<Psbt, Throwable> {
    val method = constructionType.psbtLogLabel()
    val result = when (constructionType) {
      is PsbtConstructionMethod.Regular -> createRegularPsbt(constructionType)
      is PsbtConstructionMethod.DrainAllFromUtxos -> createDrainFromUtxosPsbt(constructionType)
      is PsbtConstructionMethod.FeeBump -> createFeeBumpPsbt(constructionType)
      is PsbtConstructionMethod.FeeBumpWithDrain -> createFeeBumpWithDrainPsbt(constructionType)
      is PsbtConstructionMethod.Cpfp -> createCpfpPsbt(constructionType)
    }
    logPsbtCreationFailureIfNeeded(method, result)
    return result
  }

  override suspend fun isBalanceSpendable(): Result<Boolean, Error> =
    coroutineBinding {
      val destinationAddress = getLastUnusedAddress().bind()
      val feeRate =
        bitcoinFeeRateEstimator.estimatedFeeRateForTransaction(
          networkType = networkType,
          estimatedTransactionPriority = EstimatedTransactionPriority.THIRTY_MINUTES
        )

      createPsbt(
        recipientAddress = destinationAddress,
        amount = BitcoinTransactionSendAmount.SendAll,
        feePolicy = FeePolicy.Rate(feeRate)
      ).mapError { it as Error }
        .recoverIf(
          predicate = { it is BdkError.InsufficientFunds },
          transform = { false }
        ).map { true }
        .bind()
    }

  private fun getBalance(): Result<BitcoinBalance, Error> {
    return catchingResult {
      val balance = bdkWallet.balance()
      BitcoinBalance(
        immature = BitcoinMoney.sats(balance.immature.toSat().toLong()),
        trustedPending = BitcoinMoney.sats(balance.trustedPending.toSat().toLong()),
        untrustedPending = BitcoinMoney.sats(balance.untrustedPending.toSat().toLong()),
        confirmed = BitcoinMoney.sats(balance.confirmed.toSat().toLong()),
        spendable = BitcoinMoney.sats(balance.trustedSpendable.toSat().toLong()),
        total = BitcoinMoney.sats(balance.total.toSat().toLong())
      )
    }.mapError { SpendingWalletV2Error.BalanceRetrievalFailed(it) }
      .logFailure { "BDK2 balance retrieval failed" }
  }

  private suspend fun getTransactions(): Result<List<BitcoinTransaction>, SpendingWalletV2Error> {
    return catchingResult {
      bdkWallet.transactions().mapNotNull { canonicalTx ->
        // Get full TxDetails which includes sent/received amounts
        val txid = canonicalTx.transaction.computeTxid()
        bdkWallet.txDetails(txid)?.let { txDetails ->
          bdkTransactionMapperV2.createTransaction(
            txDetails = txDetails,
            wallet = bdkWallet,
            networkType = networkType
          )
        }
      }
    }.mapError { SpendingWalletV2Error.TransactionsRetrievalFailed(it) }
      .logFailure { "BDK2 transactions retrieval failed" }
  }

  private fun getUnspentOutputs(): Result<List<BdkUtxo>, SpendingWalletV2Error> {
    return catchingResult {
      bdkWallet.listUnspent().map { bdkTransactionMapperV2.createUtxo(it) }
    }.mapError { SpendingWalletV2Error.UnspentOutputsRetrievalFailed(it) }
      .logFailure { "BDK2 UTXO retrieval failed" }
  }

  /**
   * Creates a signed PSBT for a regular send transaction.
   * Supports both exact amount sends and send-all (drain wallet) operations.
   */
  private suspend fun createRegularPsbt(
    constructionType: PsbtConstructionMethod.Regular,
  ): Result<Psbt, Throwable> =
    withContext(Dispatchers.BdkIO) {
      validateFeePolicy(constructionType.feePolicy)?.let { return@withContext Err(it) }

      val bdkAddress = BdkV2Address(constructionType.recipientAddress.address, networkType.bdkNetworkV2)

      val bdkPsbt = catchingResult {
        TxBuilder()
          .feePolicy(constructionType.feePolicy)
          .sendTo(bdkAddress.scriptPubkey(), constructionType.amount)
          .coinSelectionStrategy(constructionType.coinSelectionStrategy)
          .setExactSequence(RBF_SEQUENCE)
          .finish(bdkWallet)
      }.getOrElse {
        return@withContext Err(it.toBdkError())
      }

      persistSignAndFinalize(bdkPsbt)
    }

  /**
   * Creates a signed PSBT that drains specific UTXOs to a recipient address.
   * Used for UTXO consolidation.
   */
  private suspend fun createDrainFromUtxosPsbt(
    constructionType: PsbtConstructionMethod.DrainAllFromUtxos,
  ): Result<Psbt, Throwable> =
    withContext(Dispatchers.BdkIO) {
      validateFeePolicy(constructionType.feePolicy)?.let { return@withContext Err(it) }

      val bdkAddress = BdkV2Address(constructionType.recipientAddress.address, networkType.bdkNetworkV2)

      val bdkPsbt = catchingResult {
        TxBuilder()
          .drainWallet()
          .drainTo(bdkAddress.scriptPubkey())
          .feePolicy(constructionType.feePolicy)
          .selectOnlyUtxos(constructionType.utxos)
          .setExactSequence(RBF_SEQUENCE)
          .finish(bdkWallet)
      }.getOrElse {
        return@withContext Err(it.toBdkError())
      }

      persistSignAndFinalize(bdkPsbt)
    }

  /**
   * Creates a signed PSBT that bumps the fee of an existing transaction.
   */
  private suspend fun createFeeBumpPsbt(
    constructionType: PsbtConstructionMethod.FeeBump,
  ): Result<Psbt, Throwable> =
    withContext(Dispatchers.BdkIO) {
      val requestedSatsPerVb = constructionType.feeRate.satsPerVByte
      if (!requestedSatsPerVb.isFinite() || requestedSatsPerVb <= 0f) {
        return@withContext Err(SpendingWalletV2Error.InvalidFeeRate(requestedSatsPerVb))
      }

      val bdkFeeRate = constructionType.feeRate.toBdkV2FeeRate()

      val bdkPsbt = catchingResult {
        val txid = Txid.fromString(constructionType.txid)
        BumpFeeTxBuilder(txid, bdkFeeRate)
          .setExactSequence(RBF_SEQUENCE)
          .finish(bdkWallet)
      }.getOrElse {
        return@withContext Err(it.toBdkError())
      }

      persistSignAndFinalize(bdkPsbt)
    }

  /**
   * Creates a fee-bumped PSBT with output shrinking using BumpFeeTxBuilder.drainTo().
   *
   * Used for sweeps and single-UTXO consolidations where the standard fee bump
   * cannot add inputs to cover the fee increase. Instead, the output amount is reduced.
   */
  private suspend fun createFeeBumpWithDrainPsbt(
    constructionType: PsbtConstructionMethod.FeeBumpWithDrain,
  ): Result<Psbt, Throwable> =
    withContext(Dispatchers.BdkIO) {
      val requestedSatsPerVb = constructionType.feeRate.satsPerVByte
      if (!requestedSatsPerVb.isFinite() || requestedSatsPerVb <= 0f) {
        return@withContext Err(SpendingWalletV2Error.InvalidFeeRate(requestedSatsPerVb))
      }

      val bdkPsbt = catchingResult {
        val txid = Txid.fromString(constructionType.txid)
        val drainScript = constructionType.drainToScript.toBdkV2Script()
        // Preserve an RBF-signaling nSequence from the original tx to avoid breaking CSV semantics.
        // Fallback to the standard RBF sequence if none are found.
        val rbfSequence = bdkWallet.getTx(txid)
          ?.transaction
          ?.input()
          ?.map { it.sequence }
          ?.filter { it < BIP125_SEQUENCE_SIGNAL_THRESHOLD }
          ?.minOrNull()
        val builder = BumpFeeTxBuilder(txid, constructionType.feeRate.toBdkV2FeeRate())
        val builderWithSequence = if (rbfSequence != null) {
          builder.setExactSequence(rbfSequence)
        } else {
          logWarn { "FeeBumpWithDrain: no RBF-signaling input sequence found, using default RBF sequence" }
          builder.setExactSequence(RBF_SEQUENCE)
        }
        builderWithSequence
          .drainTo(drainScript)
          .finish(bdkWallet)
      }.getOrElse {
        return@withContext Err(it.toBdkError())
      }

      persistSignAndFinalize(bdkPsbt)
    }

  /**
   * Creates a signed CPFP PSBT that spends a single UTXO and drains to a recipient address.
   */
  private suspend fun createCpfpPsbt(
    constructionType: PsbtConstructionMethod.Cpfp,
  ): Result<Psbt, Throwable> =
    withContext(Dispatchers.BdkIO) {
      coroutineBinding {
        validateFeePolicy(constructionType.feePolicy)?.let { Err(it).bind<Psbt>() }

        val bdkAddress = catchingResult {
          BdkV2Address(constructionType.recipientAddress.address, networkType.bdkNetworkV2)
        }.mapError { it.toBdkError() }.bind()

        val bdkPsbt = catchingResult {
          TxBuilder()
            .addUtxo(constructionType.utxoOutpoint.toOutPoint())
            .manuallySelectedOnly()
            .drainWallet()
            .drainTo(bdkAddress.scriptPubkey())
            .feePolicy(constructionType.feePolicy)
            .setExactSequence(RBF_SEQUENCE)
            .finish(bdkWallet)
        }.mapError { it.toBdkError() }.bind()

        persistSignAndFinalize(bdkPsbt).bind()
      }
    }

  /**
   * Persists wallet state, signs the PSBT, and converts to our Psbt type.
   * Common finalization logic shared by all PSBT creation methods.
   *
   * This function owns its dispatcher to ensure blocking I/O runs on the correct thread,
   * regardless of how callers invoke it.
   */
  private suspend fun persistSignAndFinalize(bdkPsbt: BdkV2Psbt): Result<Psbt, Throwable> =
    withContext(Dispatchers.BdkIO) {
      catchingResult {
        bdkWallet.persist(persister)
      }.getOrElse {
        return@withContext Err(SpendingWalletV2Error.PersistFailed(it))
      }

      catchingResult {
        bdkWallet.sign(bdkPsbt)
      }.getOrElse {
        return@withContext Err(it.toBdkError())
      }

      catchingResult {
        bdkPsbt.toPsbt()
      }.getOrElse {
        return@withContext Err(it.toPsbtConversionError())
      }.let { Ok(it) }
    }

  private fun logPsbtCreationFailureIfNeeded(
    method: String,
    result: Result<Psbt, Throwable>,
  ) {
    if (result.isErr) {
      val errorName = result.error::class.simpleName ?: "UnknownError"
      logWarn { "BDK2 PSBT creation failed (method=$method, error=$errorName)" }
    }
  }

  private fun PsbtConstructionMethod.psbtLogLabel(): String =
    when (this) {
      is PsbtConstructionMethod.Regular -> "regular"
      is PsbtConstructionMethod.DrainAllFromUtxos -> "drain"
      is PsbtConstructionMethod.FeeBump -> "fee_bump"
      is PsbtConstructionMethod.FeeBumpWithDrain -> "fee_bump_with_drain"
      is PsbtConstructionMethod.Cpfp -> "cpfp"
    }

  private fun BdkV2Psbt.toPsbt(): Psbt {
    val feeSats = fee()
    val tx = extractTx()
    val txid = tx.computeTxid().toString()

    val amountSats = tx.output()
      .filter { !bdkWallet.isMine(it.scriptPubkey) }
      .sumOf { it.value.toSat() }

    return Psbt(
      id = txid,
      base64 = serialize(),
      fee = Fee(amount = BitcoinMoney.sats(feeSats.toLong())),
      vsize = tx.vsize().toLong(),
      numOfInputs = tx.input().size,
      amountSats = amountSats,
      inputs = tx.input().map { it.toBdkTxIn() }.toSet(),
      outputs = tx.output().map { it.toBdkTxOut() }.toSet()
    )
  }

  private fun Throwable.toPsbtConversionError(): BdkError =
    when (this) {
      is PsbtException.MissingUtxo ->
        BdkError.Psbt(this, "PSBT is missing input UTXO data. Sync wallet and retry.")
      is ExtractTxException.MissingInputValue ->
        BdkError.Psbt(this, "PSBT inputs are missing value information. Sync wallet and retry.")
      else -> toBdkError()
    }

  private fun validateFeePolicy(feePolicy: FeePolicy): SpendingWalletV2Error.InvalidFeeRate? {
    if (feePolicy is FeePolicy.Rate) {
      val satsPerVb = feePolicy.feeRate.satsPerVByte
      if (!satsPerVb.isFinite() || satsPerVb <= 0f) {
        return SpendingWalletV2Error.InvalidFeeRate(satsPerVb)
      }
    }
    return null
  }
}
