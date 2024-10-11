package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.transactions.FeeBumpAllowShrinkingChecker.AllowShrinkingError
import build.wallet.bitcoin.transactions.FeeBumpAllowShrinkingChecker.AllowShrinkingError.*
import build.wallet.ensureNotNull
import build.wallet.feature.flags.SpeedUpAllowShrinkingFeatureFlag
import build.wallet.feature.isEnabled
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

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
      txid = transaction.id,
      transactionOutputs = transaction.outputs,
      walletUnspentOutputs = walletUnspentOutputs
    )
  }

  override suspend fun allowShrinkingOutputScript(
    txid: String,
    bdkWallet: BdkWallet,
  ): Result<BdkScript?, AllowShrinkingError> {
    return coroutineBinding {
      val unspentOutputs = bdkWallet.listUnspent().result.mapError {
        FailedToListUnspentOutputs(cause = it)
      }.bind()

      val walletTransactions = bdkWallet.listTransactions(includeRaw = true).result.mapError {
        FailedToListTransactions(cause = it)
      }.bind()

      val matchingTransaction = walletTransactions.find { it.txid == txid }?.transaction
      ensureNotNull(matchingTransaction) {
        FailedToFindTransaction()
      }

      allowShrinkingOutput(
        txid = txid,
        transactionOutputs = matchingTransaction.output(),
        walletUnspentOutputs = unspentOutputs
      )
    }
  }

  private fun allowShrinkingOutput(
    txid: String,
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
      (
        walletUnspentOutputs.single().outPoint.vout != 0.toUInt() ||
          walletUnspentOutputs.single().outPoint.txid != txid
      )
    ) {
      return null
    }

    return transactionOutputs.single().scriptPubkey
  }
}
