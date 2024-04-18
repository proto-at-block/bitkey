package build.wallet.bitcoin.wallet

import build.wallet.LoadableValue
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.Psbt
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Represents a watching bitcoin wallet. Created using public descriptor.
 *
 * Provides APIs to sync the wallet, generate new deposit addresses, get the current balance and
 * the transaction history, create (unsigned) psbts.
 *
 * Instances of this interfaces can be created using [WatchingWalletProvider].
 */
@Suppress("TooManyFunctions")
interface WatchingWallet {
  /**
   * The identifier of the wallet. Unique to descriptor associated with the wallet. Does not expose
   * any sensitive information. Used for purposes of managing the wallet -
   */
  val identifier: String

  /**
   * Initializes the balance and transactions, updating them from [InitialLoading] to [LoadedValue].
   */
  suspend fun initializeBalanceAndTransactions()

  /**
   * On demand request to sync wallet (balance, transactions, etc).
   */
  suspend fun sync(): Result<Unit, Error>

  /**
   * Launches a non-blocking coroutine to call [sync] for the wallet every [interval].
   */
  fun launchPeriodicSync(
    scope: CoroutineScope,
    interval: Duration,
  )

  /**
   * Generates a new address for the wallet.
   */
  suspend fun getNewAddress(): Result<BitcoinAddress, Error>

  /**
   * Returns the last unused address of the wallet.
   */
  suspend fun getLastUnusedAddress(): Result<BitcoinAddress, Error>

  /**
   * Checks if the given address belongs to the [WatchingWallet]
   */
  suspend fun isMine(address: BitcoinAddress): Result<Boolean, Error>

  /**
   * Checks if the scriptPubKey belongs to the [WatchingWallet]
   */
  suspend fun isMine(scriptPubKey: BdkScript): Result<Boolean, Error>

  /**
   * Emits the current balance of the wallet. The balance is pulled after every sync.
   *
   * At first emits [LoadableValue.InitialLoading] and then [LoadableValue.LoadedValue] after
   * every successful [sync].
   */
  fun balance(): Flow<LoadableValue<BitcoinBalance>>

  /**
   * Emits the current lost of transactions of the wallet. The balance is pulled after every sync.
   *
   * At first emits [LoadableValue.InitialLoading] and then [LoadableValue.LoadedValue] after
   * every successful [sync].
   */
  fun transactions(): Flow<LoadableValue<List<BitcoinTransaction>>>

  /**
   * Emits current list of unspent transaction outputs. It is pulled after every sync.
   *
   * At first emits [LoadableValue.InitialLoading] and then [LoadableValue.LoadedValue] after
   * every successful [sync].
   */
  fun unspentOutputs(): Flow<LoadableValue<List<BdkUtxo>>>

  /**
   * Creates a PSBT using utxos from this wallet.
   *
   * Note: Does not actually sign the PSBT using this wallet.
   *
   * @param recipientAddress The address to send the funds to.
   * @param amount the amount to send - either exact or drain.
   * @param feeRate the fee rate to use for the transaction.
   * @param exactFee the exact fee to use for the transaction.
   *
   * TODO(W-3862): use a sealed interface for fee and feeRate
   */
  suspend fun createPsbt(
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
    feePolicy: FeePolicy,
  ): Result<Psbt, Throwable>
}
