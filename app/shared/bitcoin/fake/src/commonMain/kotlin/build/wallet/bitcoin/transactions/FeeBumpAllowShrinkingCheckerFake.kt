package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bdk.bindings.BdkWallet

class FeeBumpAllowShrinkingCheckerFake(
  var shrinkingOutput: BdkScript? = null,
) : FeeBumpAllowShrinkingChecker {
  override fun transactionSupportsAllowShrinking(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: List<BdkUtxo>,
  ): Boolean {
    return shrinkingOutput != null
  }

  override fun allowShrinkingOutputScript(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: List<BdkUtxo>,
  ): BdkScript? {
    return shrinkingOutput
  }

  override suspend fun allowShrinkingOutputScript(
    txid: String,
    bdkWallet: BdkWallet,
  ): BdkScript? {
    return shrinkingOutput
  }

  fun reset() {
    shrinkingOutput = null
  }
}
