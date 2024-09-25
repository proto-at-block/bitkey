package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.*
import build.wallet.feature.flags.SpeedUpAllowShrinkingFeatureFlag
import build.wallet.feature.isEnabled

class FeeBumpAllowShrinkingCheckerImpl(
  private val allowShrinkingFeatureFlag: SpeedUpAllowShrinkingFeatureFlag,
) : FeeBumpAllowShrinkingChecker {
  override fun transactionSupportsAllowShrinking(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: List<BdkUtxo>,
  ): Boolean {
    if (!allowShrinkingFeatureFlag.isEnabled()) {
      return false
    }
    return allowShrinkingOutputScript(transaction, walletUnspentOutputs) != null
  }

  override fun allowShrinkingOutputScript(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: List<BdkUtxo>,
  ): BdkScript? {
    return allowShrinkingOutput(
      transaction.outputs,
      walletUnspentOutputs
    )
  }

  override suspend fun allowShrinkingOutputScript(
    txid: String,
    bdkWallet: BdkWallet,
  ): BdkScript? {
    val unspent = bdkWallet.listUnspent().get() ?: return null

    val matchingTransaction =
      bdkWallet.listTransactions(includeRaw = true).get()?.find { it.txid == txid }?.transaction
        ?: return null

    return allowShrinkingOutput(
      transactionOutputs = matchingTransaction.output(),
      walletUnspentOutputs = unspent
    )
  }

  private fun allowShrinkingOutput(
    transactionOutputs: List<BdkTxOut>,
    walletUnspentOutputs: List<BdkUtxo>,
  ): BdkScript? {
    if (!allowShrinkingFeatureFlag.isEnabled()) {
      return null
    }

    if (transactionOutputs.size != 1) {
      return null
    }

    if (walletUnspentOutputs.size > 1) {
      return null
    }

    // If there's an unspent output that is NOT the transaction's output, we do not use allow_shrinking
    // as that unspent output can be selected to pay for the fee bump instead.
    if (walletUnspentOutputs.isNotEmpty() &&
      walletUnspentOutputs.single().txOut.scriptPubkey != transactionOutputs.single().scriptPubkey
    ) {
      return null
    }

    return transactionOutputs.single().scriptPubkey
  }
}
