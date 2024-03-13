package build.wallet.bitcoin.wallet

import build.wallet.LoadableValue
import build.wallet.LoadableValue.InitialLoading
import build.wallet.LoadableValue.LoadedValue
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkAddressIndex
import build.wallet.bdk.bindings.BdkAddressIndex.LAST_UNUSED
import build.wallet.bdk.bindings.BdkAddressIndex.NEW
import build.wallet.bdk.bindings.BdkBalance
import build.wallet.bdk.bindings.BdkBumpFeeTxBuilderFactory
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkIO
import build.wallet.bdk.bindings.BdkPartiallySignedTransaction
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.BdkTxBuilderFactory
import build.wallet.bdk.bindings.BdkTxBuilderResult
import build.wallet.bdk.bindings.BdkWallet
import build.wallet.bdk.bindings.getAddress
import build.wallet.bdk.bindings.getBalance
import build.wallet.bdk.bindings.isMine
import build.wallet.bdk.bindings.listTransactions
import build.wallet.bdk.bindings.sign
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.bdk.BdkTransactionMapper
import build.wallet.bitcoin.bdk.BdkWalletSyncer
import build.wallet.bitcoin.bdk.bdkNetwork
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod.BumpFee
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod.Regular
import build.wallet.catching
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.time.Duration

class SpendingWalletImpl(
  override val identifier: String,
  private val bdkWallet: BdkWallet,
  private val networkType: BitcoinNetworkType,
  private val bdkTransactionMapper: BdkTransactionMapper,
  private val bdkWalletSyncer: BdkWalletSyncer,
  private val bdkPsbtBuilder: BdkPartiallySignedTransactionBuilder,
  private val bdkTxBuilderFactory: BdkTxBuilderFactory,
  private val bdkAddressBuilder: BdkAddressBuilder,
  private val bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory,
) : SpendingWallet {
  private val balanceState = MutableStateFlow<LoadableValue<BitcoinBalance>>(InitialLoading)
  private val transactionsState =
    MutableStateFlow<LoadableValue<List<BitcoinTransaction>>>(InitialLoading)

  override suspend fun initializeBalanceAndTransactions() {
    getBalance().onSuccess { balanceState.value = LoadedValue(it) }
    getTransactions().onSuccess { transactionsState.value = LoadedValue(it) }
  }

  override suspend fun sync(): Result<Unit, Error> =
    binding {
      bdkWalletSyncer.sync(bdkWallet, networkType)
        .logFailure { "Error syncing wallet" }
        .bind()

      getTransactions()
        .logFailure { "Error getting transactions" }
        .bind()
        .also { transactionsState.value = LoadedValue(it) }

      getBalance()
        .logFailure { "Error getting balance" }
        .bind()
        .also { balanceState.value = LoadedValue(it) }
    }

  override fun launchPeriodicSync(
    scope: CoroutineScope,
    interval: Duration,
  ) {
    // Set up periodic syncs based on the given frequency
    scope.launch(Dispatchers.IO) {
      while (true) {
        sync()
        delay(interval)
      }
    }
  }

  override suspend fun getNewAddress(): Result<BitcoinAddress, Error> {
    return getAddress(NEW)
  }

  override suspend fun getLastUnusedAddress(): Result<BitcoinAddress, Error> {
    return getAddress(LAST_UNUSED)
  }

  private suspend fun getAddress(index: BdkAddressIndex): Result<BitcoinAddress, Error> {
    return bdkWallet
      .getAddress(index).result
      .map { BitcoinAddress(it.address.asString()) }
      .logFailure { "Error getting new address for wallet." }
  }

  override suspend fun isMine(address: BitcoinAddress): Result<Boolean, Error> {
    return bdkAddressBuilder.build(address.address, networkType.bdkNetwork)
      .result
      .flatMap { bdkAddress ->
        bdkWallet.isMine(bdkAddress.scriptPubkey()).result
      }
  }

  override fun balance(): Flow<LoadableValue<BitcoinBalance>> = balanceState

  override fun transactions(): Flow<LoadableValue<List<BitcoinTransaction>>> = transactionsState

  private suspend fun getBalance(): Result<BitcoinBalance, Error> {
    return bdkWallet
      .getBalance().result
      .map { it.toBitcoinBalance() }
      .logFailure { "Error getting balance for wallet." }
  }

  override suspend fun signPsbt(psbt: Psbt): Result<Psbt, Throwable> =
    binding {
      val bdkPsbt =
        withContext(Dispatchers.BdkIO) {
          bdkPsbtBuilder.build(psbt.base64).result.bind().also {
            bdkWallet.sign(it).result.bind()
          }
        }

      Result.catching { psbt.copy(base64 = bdkPsbt.serialize()) }.bind()
    }.logFailure { "Error signing a psbt." }

  private suspend fun getTransactions(): Result<List<BitcoinTransaction>, BdkError> =
    binding {
      val bdkTransactions = bdkWallet.listTransactions(includeRaw = true).result.bind()

      bdkTransactions
        .map {
          bdkTransactionMapper.createTransaction(
            bdkTransaction = it,
            bdkNetwork = networkType.bdkNetwork,
            bdkWallet = bdkWallet
          )
        }
        // Desc sorting by timestamp, either confirmation time or broadcast time,
        // making sure that pending transactions without a stored broadcast time
        // still go first
        .sortedByDescending { txn ->
          when (val status = txn.confirmationStatus) {
            is BitcoinTransaction.ConfirmationStatus.Confirmed -> status.blockTime.timestamp
            is BitcoinTransaction.ConfirmationStatus.Pending ->
              txn.broadcastTime
                ?: Instant.DISTANT_FUTURE
          }
        }
    }.logFailure { "Error getting bitcoin transactions for wallet." }

  private fun BdkBalance.toBitcoinBalance() =
    BitcoinBalance(
      immature = BitcoinMoney.sats(immature),
      trustedPending = BitcoinMoney.sats(trustedPending),
      untrustedPending = BitcoinMoney.sats(untrustedPending),
      confirmed = BitcoinMoney.sats(confirmed),
      spendable = BitcoinMoney.sats(spendable),
      total = BitcoinMoney.sats(total)
    )

  override suspend fun createSignedPsbt(
    constructionType: PsbtConstructionMethod,
  ): Result<Psbt, Throwable> {
    return when (constructionType) {
      is Regular ->
        createPsbt(
          constructionType.recipientAddress,
          constructionType.amount,
          constructionType.feePolicy
        ).flatMap { signPsbt(it) }

      is BumpFee ->
        createFeeBumpedPsbt(constructionType.txid, constructionType.feeRate)
          .flatMap { signPsbt(it) }
    }
  }

  /**
   * Creates a PSBT using BDK. If [bdkWallet] was created with signing descriptor, the resulting
   * psbt will be signed appropriately. Otherwise, an unsigned psbt will be created.
   */
  override suspend fun createPsbt(
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
    feePolicy: FeePolicy,
  ): Result<Psbt, BdkError> =
    binding {
      withContext(Dispatchers.BdkIO) {
        val txBuilderResult =
          bdkTxBuilderFactory.txBuilder()
            .run {
              val bdkAddress =
                bdkAddressBuilder.build(recipientAddress.address, networkType.bdkNetwork)
                  .result
                  .logFailure { "Error creating BdkAddress" }
                  .bind()

              when (amount) {
                is BitcoinTransactionSendAmount.ExactAmount -> {
                  addRecipient(
                    script = bdkAddress.scriptPubkey(),
                    amount = amount.money.fractionalUnitValue
                  )
                }

                is BitcoinTransactionSendAmount.SendAll -> {
                  drainTo(address = bdkAddress).drainWallet()
                }
              }
            }
            .run {
              when (feePolicy) {
                is FeePolicy.Absolute ->
                  feeAbsolute(
                    feePolicy.fee.amount.fractionalUnitValue.longValue()
                  )

                is FeePolicy.Rate -> feeRate(satPerVbyte = feePolicy.feeRate.satsPerVByte)
                is FeePolicy.MinRelayRate -> this
              }
            }
            .enableRbf()
            .finish(bdkWallet)
            .result
            .bind()

        txBuilderResult
          .getPsbt(bdkWallet)
          .bind()
      }
    }

  /*
   * Creates a fee-bumped PSBT with BDK
   */
  private suspend fun createFeeBumpedPsbt(
    txid: String,
    feeRate: FeeRate,
  ): Result<Psbt, Throwable> =
    binding {
      withContext(Dispatchers.BdkIO) {
        val psbtResult =
          bdkBumpFeeTxBuilderFactory.bumpFeeTxBuilder(txid, feeRate.satsPerVByte)
            .enableRbf()
            .finish(bdkWallet)
            .result
            .bind()

        psbtResult
          .toPsbt(bdkWallet)
          .bind()
      }
    }
}

private suspend fun BdkTxBuilderResult.getPsbt(myWallet: BdkWallet): Result<Psbt, BdkError> =
  binding { psbt.toPsbt(myWallet = myWallet).bind() }

private suspend fun BdkPartiallySignedTransaction.toPsbt(
  myWallet: BdkWallet,
): Result<Psbt, BdkError> =
  binding {
    when (val feeSats = feeAmount()?.toBigInteger()) {
      null -> {
        val message = "Psbt is missing network fee amount. psbt=${serialize()}"
        Err(BdkError.Psbt(message = message, cause = IllegalStateException(message)))
      }

      else -> {
        val amountSats =
          extractTx().output()
            .filter { !myWallet.isMine(it.scriptPubkey).result.bind() }
            .fold(0UL) { acc, output ->
              acc + output.value
            }

        Ok(
          Psbt(
            id = txid(),
            base64 = serialize(),
            fee = BitcoinMoney.sats(feeSats),
            baseSize = extractTx().size().toLong(),
            numOfInputs = extractTx().input().size,
            amountSats = amountSats
          )
        )
      }
    }.bind()
  }
