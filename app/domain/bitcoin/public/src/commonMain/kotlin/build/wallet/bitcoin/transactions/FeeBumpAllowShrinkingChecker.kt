package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bdk.bindings.BdkWallet
import com.github.michaelbull.result.Result

interface FeeBumpAllowShrinkingChecker {
  /**
   * Determines whether a given transaction can be sped up using allow_shrinking.
   *
   * The rules for leveraging allowShrinking are as follows:
   *  1. Transaction must have exactly one output
   *  2. The wallet may have 0 or 1 unspent outputs
   *  3. If there is 1 wallet unspent output, it must match the output of the transaction.
   *
   * This function leverages [allowShrinkingOutputScript] under the hood.
   */
  fun transactionSupportsAllowShrinking(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: List<BdkUtxo>,
  ): Boolean

  /**
   * The output to be used with allow_shrinking, if the given transaction can be sped up
   * using shrinking; otherwise returns null.
   */
  fun allowShrinkingOutputScript(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: List<BdkUtxo>,
  ): BdkScript?

  /**
   * The output to be used with allow_shrinking, if the given transaction can be sped up
   * using shrinking; otherwise returns null.
   *
   * Returns an [AllowShrinkingError] if we're in an unexpected state, such as being unable to
   * locate the [txid] in the provided [bdkWallet].
   *
   * Looks up the corresponding transaction for [txid] using the provided [bdkWallet].
   */
  suspend fun allowShrinkingOutputScript(
    txid: String,
    bdkWallet: BdkWallet,
  ): Result<BdkScript?, AllowShrinkingError>

  sealed class AllowShrinkingError : Error() {
    /**
     * Failed to list the wallet's unspent outputs
     */
    data class FailedToListUnspentOutputs(
      override val message: String = "Failed to list unspent outputs",
      override val cause: Throwable,
    ) : AllowShrinkingError()

    /**
     * Failed to find a matching transaction in the wallet for the provided txid
     */
    data class FailedToFindTransaction(
      override val message: String = "Failed to find transaction in wallet",
    ) : AllowShrinkingError()

    /**
     * Failed to list the wallet's transactions
     */
    data class FailedToListTransactions(
      override val message: String = "Failed to list wallet transactions",
      override val cause: Throwable,
    ) : AllowShrinkingError()
  }
}
