package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.bdk.BdkTransactionMapperV2
import build.wallet.bitcoin.bdk.bdkNetworkV2
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.coroutines.flow.launchTicker
import build.wallet.money.BitcoinMoney
import build.wallet.platform.app.AppSessionManager
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.fold
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
import uniffi.bdk.KeychainKind
import uniffi.bdk.Persister
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import uniffi.bdk.Address as BdkV2Address
import uniffi.bdk.Script as BdkV2Script
import uniffi.bdk.Wallet as BdkV2Wallet

class WalletV2Impl(
  override val identifier: String,
  override val networkType: BitcoinNetworkType,
  private val bdkWallet: BdkV2Wallet,
  private val persister: Persister,
  private val appSessionManager: AppSessionManager,
  private val bdkTransactionMapperV2: BdkTransactionMapperV2,
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
    // TODO(W-15008): Implement sync using ElectrumClient or EsploraClient
    return Err(WalletV2Error.NotImplemented("sync"))
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
    return runCatching {
      val addressInfo = bdkWallet.revealNextAddress(KeychainKind.EXTERNAL)
      bdkWallet.persist(persister)
      BitcoinAddress(addressInfo.address.toString())
    }.fold(
      onSuccess = { Ok(it) },
      onFailure = { Err(WalletV2Error.AddressGenerationFailed(it)) }
    )
  }

  override suspend fun peekAddress(index: UInt): Result<BitcoinAddress, Error> {
    return runCatching {
      val addressInfo = bdkWallet.peekAddress(KeychainKind.EXTERNAL, index)
      BitcoinAddress(addressInfo.address.toString())
    }.fold(
      onSuccess = { Ok(it) },
      onFailure = { Err(WalletV2Error.AddressPeekFailed(index, it)) }
    )
  }

  // TODO: rename this to nextUnused when we remove legacy bdk impl
  override suspend fun getLastUnusedAddress(): Result<BitcoinAddress, Error> {
    return runCatching {
      val addressInfo = bdkWallet.nextUnusedAddress(KeychainKind.EXTERNAL)
      bdkWallet.persist(persister)
      BitcoinAddress(addressInfo.address.toString())
    }.fold(
      onSuccess = { Ok(it) },
      onFailure = { Err(WalletV2Error.LastUnusedAddressFailed(it)) }
    )
  }

  override suspend fun isMine(address: BitcoinAddress): Result<Boolean, Error> {
    return runCatching {
      val bdkAddress = BdkV2Address(address.address, networkType.bdkNetworkV2)
      bdkWallet.isMine(bdkAddress.scriptPubkey())
    }.fold(
      onSuccess = { Ok(it) },
      onFailure = { Err(WalletV2Error.IsMineCheckFailed(it)) }
    )
  }

  override suspend fun isMine(scriptPubKey: BdkScript): Result<Boolean, Error> {
    return runCatching {
      val script = BdkV2Script(scriptPubKey.rawOutputScript.toUByteArray().toByteArray())
      bdkWallet.isMine(script)
    }.fold(
      onSuccess = { Ok(it) },
      onFailure = { Err(WalletV2Error.IsMineCheckFailed(it)) }
    )
  }

  override fun balance(): Flow<BitcoinBalance> = balanceState.filterNotNull()

  override fun transactions(): Flow<List<BitcoinTransaction>> = transactionsState.filterNotNull()

  override fun unspentOutputs(): Flow<List<BdkUtxo>> = unspentOutputsState.filterNotNull()

  private fun getBalance(): Result<BitcoinBalance, Error> {
    return runCatching {
      val balance = bdkWallet.balance()
      BitcoinBalance(
        immature = BitcoinMoney.sats(balance.immature.toSat().toLong()),
        trustedPending = BitcoinMoney.sats(balance.trustedPending.toSat().toLong()),
        untrustedPending = BitcoinMoney.sats(balance.untrustedPending.toSat().toLong()),
        confirmed = BitcoinMoney.sats(balance.confirmed.toSat().toLong()),
        spendable = BitcoinMoney.sats(balance.trustedSpendable.toSat().toLong()),
        total = BitcoinMoney.sats(balance.total.toSat().toLong())
      )
    }.fold(
      onSuccess = { Ok(it) },
      onFailure = { Err(WalletV2Error.BalanceRetrievalFailed(it)) }
    )
  }

  private suspend fun getTransactions(): Result<List<BitcoinTransaction>, WalletV2Error> {
    return runCatching {
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
    }.fold(
      onSuccess = { Ok(it) },
      onFailure = { Err(WalletV2Error.TransactionsRetrievalFailed(it)) }
    )
  }

  private fun getUnspentOutputs(): Result<List<BdkUtxo>, WalletV2Error> {
    return runCatching {
      bdkWallet.listUnspent().map { bdkTransactionMapperV2.createUtxo(it) }
    }.fold(
      onSuccess = { Ok(it) },
      onFailure = { Err(WalletV2Error.UnspentOutputsRetrievalFailed(it)) }
    )
  }

  override suspend fun createPsbt(
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
    feePolicy: FeePolicy,
    coinSelectionStrategy: CoinSelectionStrategy,
  ): Result<Psbt, Throwable> {
    return Err(WalletV2Error.NotImplemented("createPsbt"))
  }

  override suspend fun signPsbt(psbt: Psbt): Result<Psbt, Throwable> {
    return Err(WalletV2Error.NotImplemented("signPsbt"))
  }

  override suspend fun createSignedPsbt(
    constructionType: PsbtConstructionMethod,
  ): Result<Psbt, Throwable> {
    return Err(WalletV2Error.NotImplemented("createSignedPsbt"))
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
}
