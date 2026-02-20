package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkIO
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressInfo
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.bdk.BdkTransactionMapperV2
import build.wallet.bitcoin.bdk.BdkWalletSyncerV2
import build.wallet.bitcoin.bdk.bdkNetworkV2
import build.wallet.bitcoin.bdk.coinSelectionStrategy
import build.wallet.bitcoin.bdk.feePolicy
import build.wallet.bitcoin.bdk.selectOnlyUtxos
import build.wallet.bitcoin.bdk.sendTo
import build.wallet.bitcoin.bdk.toBdkError
import build.wallet.bitcoin.bdk.toBdkTxIn
import build.wallet.bitcoin.bdk.toBdkTxOut
import build.wallet.bitcoin.bdk.toBdkV2Amount
import build.wallet.bitcoin.bdk.toBdkV2FeeRate
import build.wallet.bitcoin.bdk.toBdkV2Script
import build.wallet.bitcoin.bdk.toOutPoint
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.catchingResult
import build.wallet.coroutines.flow.launchTicker
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.money.BitcoinMoney
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.recoverIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import uniffi.bdk.BumpFeeTxBuilder
import uniffi.bdk.Descriptor
import uniffi.bdk.DescriptorException
import uniffi.bdk.ExtractTxException
import uniffi.bdk.KeychainKind
import uniffi.bdk.Persister
import uniffi.bdk.PsbtException
import uniffi.bdk.TxBuilder
import uniffi.bdk.Txid
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
      is PsbtConstructionMethod.ManualFeeBump -> createManualFeeBumpPsbt(constructionType)
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
   * Creates a signed PSBT for a manual fee bump where the output amount is reduced
   * to cover the increased fee (output shrinking).
   *
   * Used for sweeps and single-UTXO consolidations where BDK's BumpFeeTxBuilder
   * cannot handle the case because there are no additional inputs to pull in.
   *
   * Uses drainWallet() + drainTo() to ensure single output with no change.
   * Uses feeAbsolute() to avoid fee recalculation based on vsize.
   */
  private suspend fun createManualFeeBumpPsbt(
    constructionType: PsbtConstructionMethod.ManualFeeBump,
  ): Result<Psbt, Throwable> =
    withContext(Dispatchers.BdkIO) {
      val outputScript = constructionType.outputScript.toBdkV2Script()
      val absoluteFee = constructionType.absoluteFee.amount.toBdkV2Amount()
      val expectedFeeSats = constructionType.absoluteFee.amount.fractionalUnitValue.longValue()

      // BIP125 only requires at least one input to signal RBF (sequence < BIP125_SEQUENCE_SIGNAL_THRESHOLD).
      // Since TxBuilder.setExactSequence() sets ALL inputs to the same value, we:
      // 1. Find if any input already signals RBF - if so, use that sequence
      // 2. If none signal RBF, force all to RBF_SEQUENCE
      // This is valid because a transaction is RBF-eligible as long as at least one input signals.
      //
      // Note: The fallback to RBF_SEQUENCE shouldn't occur in practice because
      // SpeedUpTransactionServiceImpl.prepareTransactionSpeedUp() already validates that the
      // transaction signals RBF before calling this method. The fallback exists as a safety net.
      val rbfSequence = constructionType.originalInputs
        .map { it.sequence }
        .filter { it < BIP125_SEQUENCE_SIGNAL_THRESHOLD }
        .minOrNull()

      val inputSequence = rbfSequence ?: run {
        logWarn { "ManualFeeBump: no RBF-signaling input found, using default RBF_SEQUENCE" }
        RBF_SEQUENCE
      }

      // For manual fee bumps, the original inputs may no longer be in the wallet's UTXO set
      // (they've been marked as "spent" after broadcast). We use addForeignUtxo() which allows
      // spending UTXOs not tracked in the wallet's UTXO set.

      val bdkPsbt = catchingResult {
        constructionType.originalInputs
          .fold(TxBuilder()) { builder, txIn ->
            val outpoint = txIn.outpoint.toOutPoint()
            val prevTxid = Txid.fromString(txIn.outpoint.txid)
            val prevTx = bdkWallet.getTx(prevTxid)
              ?: return@withContext Err(
                SpendingWalletV2Error.PreviousTransactionNotFound()
              )
            val prevTransaction = prevTx.transaction
            val prevOutput = prevTransaction.output().getOrNull(txIn.outpoint.vout.toInt())
              ?: return@withContext Err(
                SpendingWalletV2Error.PreviousOutputNotFound()
              )
            // Pass sequence directly to preserve RBF signaling (BDK default is 0xFFFFFFFF)
            builder.addForeignUtxo(outpoint, prevOutput, prevTransaction, getSatisfactionWeight(), inputSequence)
          }
          .manuallySelectedOnly()
          .drainWallet()
          .drainTo(outputScript)
          .feeAbsolute(absoluteFee)
          .finish(bdkWallet)
      }.getOrElse {
        return@withContext Err(it.toBdkError())
      }

      // === POST-BUILD ASSERTIONS ===

      // 1. Verify fee matches what we requested
      val actualFeeSats = bdkPsbt.fee().toLong()
      if (actualFeeSats != expectedFeeSats) {
        logError {
          "BDK2 manual fee bump invariant failed: fee mismatch"
        }
        return@withContext Err(
          SpendingWalletV2Error.FeeMismatch(expected = expectedFeeSats, actual = actualFeeSats)
        )
      }

      // 2. Verify exactly one output (no change was created)
      val tx = bdkPsbt.extractTx()
      if (tx.output().size != 1) {
        logError {
          "BDK2 manual fee bump invariant failed: unexpected output"
        }
        return@withContext Err(
          SpendingWalletV2Error.UnexpectedChangeOutput(outputCount = tx.output().size)
        )
      }

      // 3. Verify input count matches original inputs
      val inputCount = tx.input().size
      if (inputCount != constructionType.originalInputs.size) {
        logError {
          "BDK2 manual fee bump invariant failed: input count"
        }
        return@withContext Err(
          SpendingWalletV2Error.InputCountMismatch(
            expected = constructionType.originalInputs.size,
            actual = inputCount
          )
        )
      }

      persistSignAndFinalize(bdkPsbt)
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
      is PsbtConstructionMethod.ManualFeeBump -> "manual_fee_bump"
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

  /**
   * Computes the satisfaction weight for inputs using BDK's descriptor analysis.
   * Falls back to P2WSH 2-of-3 multisig weight (260 WU) if calculation fails.
   */
  private fun getSatisfactionWeight(): ULong {
    val descriptorString = bdkWallet.publicDescriptor(KeychainKind.EXTERNAL)
    return try {
      Descriptor(descriptorString, networkType.bdkNetworkV2).use { descriptor ->
        descriptor.maxWeightToSatisfy()
      }
    } catch (e: DescriptorException) {
      logWarn(throwable = e) { "Failed to calculate satisfaction weight, using fallback" }
      P2WSH_2OF3_SATISFACTION_WEIGHT
    }
  }

  private companion object {
    /**
     * BIP 125 sequence threshold for RBF signaling.
     * Transactions with at least one input having nSequence < this value signal opt-in RBF.
     */
    const val BIP125_SEQUENCE_SIGNAL_THRESHOLD: UInt = 0xFFFFFFFEu

    /**
     * Standard BIP 125 RBF sequence value (0xFFFFFFFD).
     */
    const val RBF_SEQUENCE: UInt = 0xFFFFFFFDu

    /**
     * Fallback satisfaction weight for P2WSH 2-of-3 multisig inputs (260 WU).
     * Used only when BDK's maxWeightToSatisfy() fails.
     */
    const val P2WSH_2OF3_SATISFACTION_WEIGHT: ULong = 260UL
  }
}
