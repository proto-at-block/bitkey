package build.wallet.bitcoin.wallet

/**
 * Errors that can occur during wallet operations in [SpendingWalletV2Impl].
 */
sealed class SpendingWalletV2Error : Error() {
  /**
   * Failed to synchronize wallet with the blockchain.
   */
  data class SyncFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * Failed to generate or retrieve a new address.
   */
  data class AddressGenerationFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * Failed to peek an address at the specified index.
   */
  data class AddressPeekFailed(
    val index: UInt,
    override val cause: Throwable,
  ) : SpendingWalletV2Error()

  /**
   * Failed to reveal an address at the specified index.
   */
  data class AddressRevealFailed(
    val index: UInt,
    override val cause: Throwable,
  ) : SpendingWalletV2Error()

  /**
   * Failed to retrieve the last unused address.
   */
  data class LastUnusedAddressFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * Failed to check if an address or script belongs to this wallet.
   */
  data class IsMineCheckFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * Failed to retrieve wallet balance.
   */
  data class BalanceRetrievalFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * Failed to retrieve wallet transactions.
   */
  data class TransactionsRetrievalFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * Failed to retrieve unspent outputs (UTXOs).
   */
  data class UnspentOutputsRetrievalFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * Failed to sign a PSBT.
   */
  data class PsbtSigningFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * The requested operation is not yet implemented.
   */
  data class NotImplemented(val operation: String) : SpendingWalletV2Error()

  /**
   * The wallet descriptor's network does not match the app's configured network.
   */
  data class NetworkMismatch(
    val walletNetwork: String,
    val appNetwork: String,
  ) : SpendingWalletV2Error()

  /**
   * The provided fee rate is invalid (not finite or not positive).
   */
  data class InvalidFeeRate(val satsPerVByte: Float) : SpendingWalletV2Error()

  /**
   * Failed to persist wallet state after a state-changing operation.
   * This can occur after PSBT creation when change addresses are generated.
   */
  data class PersistFailed(override val cause: Throwable) : SpendingWalletV2Error()
}
