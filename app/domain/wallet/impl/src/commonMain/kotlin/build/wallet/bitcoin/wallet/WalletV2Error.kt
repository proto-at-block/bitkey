package build.wallet.bitcoin.wallet

/**
 * Errors that can occur during wallet operations in [WalletV2Impl].
 */
sealed class WalletV2Error : Error() {
  /**
   * Failed to synchronize wallet with the blockchain.
   */
  data class SyncFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * Failed to generate or retrieve a new address.
   */
  data class AddressGenerationFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * Failed to peek an address at the specified index.
   */
  data class AddressPeekFailed(
    val index: UInt,
    override val cause: Throwable,
  ) : WalletV2Error()

  /**
   * Failed to retrieve the last unused address.
   */
  data class LastUnusedAddressFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * Failed to check if an address or script belongs to this wallet.
   */
  data class IsMineCheckFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * Failed to retrieve wallet balance.
   */
  data class BalanceRetrievalFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * Failed to retrieve wallet transactions.
   */
  data class TransactionsRetrievalFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * Failed to retrieve unspent outputs (UTXOs).
   */
  data class UnspentOutputsRetrievalFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * Failed to create a PSBT (Partially Signed Bitcoin Transaction).
   */
  data class PsbtCreationFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * Failed to sign a PSBT.
   */
  data class PsbtSigningFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * Failed to check if the wallet balance is spendable.
   */
  data class SpendableCheckFailed(override val cause: Throwable) : WalletV2Error()

  /**
   * The requested operation is not yet implemented.
   */
  data class NotImplemented(val operation: String) : WalletV2Error()
}
