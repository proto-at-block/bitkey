package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bdk.bindings.BdkWallet

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
   * Looks up the corresponding transaction for [txid] using the provided [bdkWallet].
   */
  suspend fun allowShrinkingOutputScript(
    txid: String,
    bdkWallet: BdkWallet,
  ): BdkScript?
}
