package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkScriptMock
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.BlockTime
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.money.BitcoinMoney
import build.wallet.money.negate
import build.wallet.money.sumOf
import build.wallet.time.someInstant
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Fake implementation of [SpendingWallet] for testing purposes.
 *
 * Fake functionality:
 * - [getNewAddress] returns a new address from a set of addresses by rotating through them.
 * - [getLastUnusedAddress] returns the last unused address. This is updated by [getNewAddress]
 * and after [receiveFunds] is called.
 * - [receiveFunds] adds a pending incoming transaction to the wallet. Calling [mineBlock] will
 * advance this transaction to confirmed status.
 * - [sendFunds] adds a pending outgoing transaction to the wallet.
 * - [mineBlock] simulates mining blocks and transaction confirmation. Advances [previousBlockTime]
 * and moves all pending transactions to confirmed status.
 * - [reset] resets the wallet to its initial state. Should be called before each test.
 * - [transactions] returns a flow of the transaction history of the wallet. Updated by [sync].
 * - [balance] returns a flow of the balance of the wallet. Derived from [transactions].
 *
 * Unsupported APIs (will throw):
 * - [signPsbt]
 * - [createPsbt]
 * - [createSignedPsbt]
 *
 * This implementation is thread safe, enforced by the usage a mutex lock.
 */
class SpendingWalletFake(
  override val identifier: String = "wallet-fake",
) : SpendingWallet {
  private val walletLock = Mutex()

  private val addresses =
    setOf(
      BitcoinAddress("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"),
      BitcoinAddress("bc1qc7slrfxkknqcq2jevvvkdgvrtdfjxvp3jx2t0d"),
      BitcoinAddress("bc1q8c6fshw2dlwun7ekn9qwf37cu2rn755upcp6el"),
      BitcoinAddress("bc1d42UNb54eBiGm0qEM0h6r2h8n532to9jtp186ns"),
      BitcoinAddress("bc1qg7k7y4cu3ju9n08skwjm9a8pt98fnefknjlhyr"),
      BitcoinAddress("bc1qnwf4vpf8mzcd5rtmwkt5t0rvy462tdsushc38e"),
      BitcoinAddress("bc1q7qryf7dvtsf8dmldj9vrgpufmcuhflmpg796t8")
    )

  private var lastUnusedAddress: BitcoinAddress = addresses.first()
  private val externalAddress: BitcoinAddress =
    BitcoinAddress("bc1q39nrg3me4ajeqpvr5xn5aydehwq3ps88k2tmnc")

  /**
   * Source of truth for the transaction history of this wallet.
   * The balance of the wallet is derived from this state as well.
   *
   * Updated by [sync].
   */
  private val transactionHistoryState =
    MutableStateFlow<List<BitcoinTransaction>?>(null)

  /**
   * Fake pending transactions associated with this wallet.
   * These transactions contribute to [transactionHistoryState] during [sync].
   */
  private val pendingTransactions = mutableListOf<BitcoinTransaction>()

  /**
   * Fake confirmed transactions associated with this wallet.
   * These transactions contribute to [transactionHistoryState] during [sync].
   */
  private val confirmedTransactions = mutableListOf<BitcoinTransaction>()

  /**
   * Fake unspent transaction outputs associated with this wallet.
   */
  private val unspentOutputsState = MutableStateFlow<List<BdkUtxo>?>(null)

  override suspend fun initializeBalanceAndTransactions() {
    transactionHistoryState.value = emptyList()
  }

  /**
   * Syncs the wallet by updating [transactionHistoryState].
   *
   * Transaction history are updated based on the current state of [pendingTransactions]
   * and [confirmedTransactions].
   */
  override suspend fun sync(): Result<Unit, Error> {
    walletLock.withLock {
      val allTransactions =
        (confirmedTransactions + pendingTransactions)
          .sortedBy { it.id }
      transactionHistoryState.value = allTransactions
    }
    return Ok(Unit)
  }

  override fun launchPeriodicSync(
    scope: CoroutineScope,
    interval: Duration,
  ) {
    scope.launch(Dispatchers.IO) {
      while (true) {
        sync()
        delay(interval)
      }
    }
  }

  /**
   * Returns the last unused address of the wallet.
   */
  override suspend fun getLastUnusedAddress(): Result<BitcoinAddress, Error> {
    return Ok(lastUnusedAddress)
  }

  /**
   * Returns a "new" address by rotating through [addresses].
   */
  override suspend fun getNewAddress(): Result<BitcoinAddress, Error> {
    walletLock.withLock {
      lastUnusedAddress = rotatedAddress()
    }
    return Ok(lastUnusedAddress)
  }

  private fun rotatedAddress(): BitcoinAddress {
    return addresses.elementAt((addresses.indexOf(lastUnusedAddress) + 1) % addresses.size)
  }

  override suspend fun isMine(address: BitcoinAddress): Result<Boolean, Error> {
    return Ok(addresses.map { it.address }.contains(address.address))
  }

  override suspend fun isMine(scriptPubKey: BdkScript): Result<Boolean, Error> {
    return Ok(scriptPubKey == BdkScriptMock().rawOutputScript())
  }

  override fun transactions(): Flow<List<BitcoinTransaction>> {
    return transactionHistoryState.filterNotNull()
  }

  override fun unspentOutputs(): Flow<List<BdkUtxo>> {
    return unspentOutputsState.filterNotNull()
  }

  /**
   * Returns the current balance of the wallet.
   *
   * Derived from [transactionHistoryState].
   */
  override fun balance(): Flow<BitcoinBalance> =
    transactionHistoryState
      .map { state ->
        val confirmedBalance =
          when (confirmedTransactions.isEmpty()) {
            true -> BitcoinMoney.zero()
            false ->
              confirmedTransactions.sumOf {
                if (it.incoming) it.total else it.total.negate()
              }
          }

        val pendingBalance =
          when (pendingTransactions.isEmpty()) {
            true -> BitcoinMoney.zero()
            false ->
              pendingTransactions.sumOf {
                if (it.incoming) it.total else it.total.negate()
              }
          }

        BitcoinBalance(
          immature = BitcoinMoney.zero(),
          trustedPending = pendingBalance,
          untrustedPending = BitcoinMoney.zero(),
          confirmed = confirmedBalance,
          spendable = pendingBalance + confirmedBalance,
          total = pendingBalance + confirmedBalance
        )
      }
      .distinctUntilChanged()

  override suspend fun signPsbt(psbt: Psbt): Result<Psbt, Throwable> {
    error("Not supported")
  }

  override suspend fun createPsbt(
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
    feePolicy: FeePolicy,
  ): Result<Psbt, Throwable> {
    error("Not supported")
  }

  override suspend fun createSignedPsbt(
    constructionType: SpendingWallet.PsbtConstructionMethod,
  ): Result<Psbt, Throwable> {
    error("Not supported")
  }

  private var transactionIndex: Int = 0

  private val defaultFee = BitcoinMoney.sats(500)

  /**
   * Add pending incoming transaction to this wallet. Calling [mineBlock] will advance this
   * transaction to confirmed status.
   */
  suspend fun receiveFunds(amount: BitcoinMoney) {
    require(!amount.isNegative)

    addTransaction(
      BitcoinTransaction(
        id = "fake-tx-$transactionIndex",
        recipientAddress =
          lastUnusedAddress.also {
            lastUnusedAddress = rotatedAddress()
          },
        broadcastTime = previousBlockTime.timestamp,
        estimatedConfirmationTime = someInstant.plus(10.toDuration(DurationUnit.MINUTES)),
        confirmationStatus = Pending,
        fee = null,
        weight = null,
        vsize = null,
        subtotal = amount,
        total = amount,
        incoming = true,
        inputs = emptyImmutableList(),
        outputs = emptyImmutableList()
      )
    )
  }

  /**
   * Add pending outgoing transaction to this wallet. Calling [mineBlock] will advance this
   * transaction to confirmed status.
   */
  suspend fun sendFunds(
    amount: BitcoinMoney,
    fee: BitcoinMoney = defaultFee,
  ) {
    require(!amount.isNegative)
    require(!fee.isNegative)

    addTransaction(
      BitcoinTransaction(
        id = "fake-tx-$transactionIndex",
        recipientAddress = externalAddress,
        broadcastTime = previousBlockTime.timestamp,
        estimatedConfirmationTime =
          previousBlockTime.timestamp.plus(
            10.toDuration(DurationUnit.MINUTES)
          ),
        confirmationStatus = Pending,
        fee = fee,
        weight = null,
        vsize = null,
        subtotal = amount,
        total = amount + fee,
        incoming = false,
        inputs = emptyImmutableList(),
        outputs = emptyImmutableList()
      )
    )
  }

  /**
   * Adds a transaction to this wallet. In order for transaction history balance to be updated,
   * call [sync].
   */
  private suspend fun addTransaction(transaction: BitcoinTransaction) {
    walletLock.withLock {
      when (transaction.confirmationStatus) {
        is Pending -> pendingTransactions += transaction
        else -> confirmedTransactions += transaction
      }
      transactionIndex += 1
    }
  }

  private val initialBlockTime =
    BlockTime(
      height = 400,
      timestamp = someInstant
    )

  /**
   * Fake block time to be used for receiving transactions. This gets advanced by [mineBlock].
   */
  private var previousBlockTime: BlockTime = initialBlockTime

  private fun nextBlockTime(previousBlockTime: BlockTime): BlockTime {
    return previousBlockTime.copy(
      height = previousBlockTime.height + 1,
      timestamp = previousBlockTime.timestamp.plus(10.minutes)
    )
  }

  /**
   * Simulates mining blocks and transaction confirmation.
   * Advances [previousBlockTime]
   *
   * Moves all pending transactions to confirmed status. This will not update transaction history
   * or balance of the wallet. Use [sync] to update transaction history and balance.
   */
  suspend fun mineBlock() {
    walletLock.withLock {
      // Advance block time
      previousBlockTime = nextBlockTime(previousBlockTime)

      // Move pending transactions to confirmed
      confirmedTransactions +=
        pendingTransactions.map {
          it.copy(confirmationStatus = Confirmed(previousBlockTime))
        }
      // Clear pending transactions
      pendingTransactions.clear()
    }
  }

  suspend fun reset() {
    walletLock.withLock {
      // Reset balance and transaction history
      transactionHistoryState.value = null

      pendingTransactions.clear()
      confirmedTransactions.clear()
      previousBlockTime = initialBlockTime
    }
  }
}
