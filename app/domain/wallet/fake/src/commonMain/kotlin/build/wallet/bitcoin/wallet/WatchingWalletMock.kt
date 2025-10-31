package build.wallet.bitcoin.wallet

import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.Psbt
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration

/**
 * Configurable mock implementation of [WatchingWallet] for testing.
 * Provides empty transactions by default but can be customized.
 */
class WatchingWalletMock(
  override val identifier: String = "test-watching-wallet",
  override val networkType: BitcoinNetworkType = BitcoinNetworkType.SIGNET,
  private val mockTransactions: List<BitcoinTransaction> = emptyList(),
  var myAddresses: List<BitcoinAddress> = emptyList(),
  var myScripts: List<BdkScript> = emptyList(),
  var getNewAddressResult: Result<BitcoinAddress, Error>? = null,
  var peekAddressResult: Result<BitcoinAddress, Error>? = null,
  var getLastUnusedAddressResult: Result<BitcoinAddress, Error>? = null,
  var createPsbtResult: Result<Psbt, Throwable>? = null,
  var balanceFlow: Flow<BitcoinBalance>? = null,
  var unspentOutputsFlow: Flow<List<BdkUtxo>>? = null,
) : WatchingWallet {
  override suspend fun initializeBalanceAndTransactions() {}

  override suspend fun sync(): Result<Unit, Error> = Ok(Unit)

  override fun launchPeriodicSync(
    scope: CoroutineScope,
    interval: Duration,
  ): Job = Job()

  override suspend fun getNewAddress(): Result<BitcoinAddress, Error> =
    getNewAddressResult ?: error("getNewAddressResult not configured in WatchingWalletMock")

  override suspend fun peekAddress(index: UInt): Result<BitcoinAddress, Error> =
    peekAddressResult ?: error("peekAddressResult not configured in WatchingWalletMock")

  override suspend fun getLastUnusedAddress(): Result<BitcoinAddress, Error> =
    getLastUnusedAddressResult ?: error("getLastUnusedAddressResult not configured in WatchingWalletMock")

  override suspend fun isMine(address: BitcoinAddress): Result<Boolean, Error> =
    Ok(address in myAddresses)

  override suspend fun isMine(scriptPubKey: BdkScript): Result<Boolean, Error> =
    Ok(scriptPubKey in myScripts)

  override fun balance(): Flow<BitcoinBalance> =
    balanceFlow ?: error("balanceFlow not configured in WatchingWalletMock")

  override fun transactions(): Flow<List<BitcoinTransaction>> = flowOf(mockTransactions)

  override fun unspentOutputs(): Flow<List<BdkUtxo>> =
    unspentOutputsFlow ?: error("unspentOutputsFlow not configured in WatchingWalletMock")

  override suspend fun createPsbt(
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
    feePolicy: FeePolicy,
    coinSelectionStrategy: CoinSelectionStrategy,
  ): Result<Psbt, Throwable> =
    createPsbtResult ?: error("createPsbtResult not configured in WatchingWalletMock")

  fun reset() {
    myAddresses = emptyList()
    myScripts = emptyList()
    getNewAddressResult = null
    peekAddressResult = null
    getLastUnusedAddressResult = null
    createPsbtResult = null
    balanceFlow = null
    unspentOutputsFlow = null
  }
}
