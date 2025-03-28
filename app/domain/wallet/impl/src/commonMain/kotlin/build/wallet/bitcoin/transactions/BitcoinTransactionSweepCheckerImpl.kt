package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.transactions.BitcoinTransactionSweepChecker.TransactionError
import build.wallet.bitcoin.transactions.BitcoinTransactionSweepChecker.TransactionError.MultipleOutputsError
import build.wallet.bitcoin.transactions.BitcoinTransactionSweepChecker.TransactionError.WalletNotEmptyError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.isOk
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import kotlinx.collections.immutable.ImmutableList

@BitkeyInject(AppScope::class)
class BitcoinTransactionSweepCheckerImpl : BitcoinTransactionSweepChecker {
  override fun isSweep(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: ImmutableList<BdkUtxo>,
  ): Boolean {
    return sweepOutput(transaction, walletUnspentOutputs).isOk()
  }

  override fun sweepOutput(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: ImmutableList<BdkUtxo>,
  ): Result<BdkTxOut, TransactionError> =
    binding {
      // Return early if the PSBT's output is not equal to 1.
      val hasOnlyOneOutput = transaction.outputs.count() == 1
      ensure(hasOnlyOneOutput) {
        MultipleOutputsError()
      }
      val transactionSweepOutput = transaction.outputs.first()

      // Sweep transactions leave no unspent outputs in the wallet.
      ensure(walletUnspentOutputs.isEmpty()) {
        WalletNotEmptyError()
      }

      transactionSweepOutput
    }
}
