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
   * Failed to create a PSBT (Partially Signed Bitcoin Transaction).
   */
  data class PsbtCreationFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * Failed to sign a PSBT.
   */
  data class PsbtSigningFailed(override val cause: Throwable) : SpendingWalletV2Error()

  /**
   * Failed to check if the wallet balance is spendable.
   */
  data class SpendableCheckFailed(override val cause: Throwable) : SpendingWalletV2Error()

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

  /**
   * The input data for manual fee bump construction is invalid.
   */
  data class InvalidInput(val reason: String) : SpendingWalletV2Error() {
    override val message: String = "Invalid input: $reason"
  }

  /**
   * The actual fee in the constructed PSBT doesn't match the requested absolute fee.
   * This indicates a bug in the manual fee bump logic.
   */
  data class FeeMismatch(
    val expected: Long,
    val actual: Long,
  ) : SpendingWalletV2Error() {
    override val message: String = "Fee mismatch: expected $expected sats, got $actual sats"
  }

  /**
   * The constructed PSBT has more than one output, indicating an unexpected change output was
   * created. Manual fee bumps should always produce exactly one output.
   */
  data class UnexpectedChangeOutput(val outputCount: Int) : SpendingWalletV2Error() {
    override val message: String = "Expected exactly 1 output, got $outputCount - change output was created"
  }

  /**
   * The number of inputs in the constructed PSBT doesn't match the number of original inputs.
   * This indicates BDK modified the input set unexpectedly.
   */
  data class InputCountMismatch(
    val expected: Int,
    val actual: Int,
  ) : SpendingWalletV2Error() {
    override val message: String = "Input count mismatch: expected $expected, got $actual"
  }

  /**
   * The previous transaction for an input could not be found in the wallet's transaction graph.
   * This can occur if the wallet was restored or the transaction history is incomplete.
   * Txid is intentionally omitted from the error payload/message to avoid logging sensitive data.
   */
  class PreviousTransactionNotFound : SpendingWalletV2Error() {
    override val message: String = "Previous transaction not found"
  }

  /**
   * The previous output for an input could not be found in the referenced previous transaction.
   * This indicates the vout index is out of bounds.
   * Txid/vout are intentionally omitted from the error payload/message to avoid logging sensitive data.
   */
  class PreviousOutputNotFound : SpendingWalletV2Error() {
    override val message: String = "Previous output not found in referenced transaction"
  }
}
