package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList

interface BitcoinTransactionSweepChecker {
  /**
   * Checks if the transaction is a sweep.
   *
   * Calls sweepOutput underneath the hood, returning true if a sweep script pubkey is derivable.
   */
  fun isSweep(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: ImmutableList<BdkUtxo>,
  ): Boolean

  /**
   * Checks if the transaction has the shape of a sweep transaction, and returns the sweep output
   * if it does.
   *
   * It uses the following conditions:
   * 1. Ensure that the unspent output list is empty.
   * 2. Ensure it has only one output.
   */
  fun sweepOutput(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: ImmutableList<BdkUtxo>,
  ): Result<BdkTxOut, TransactionError>

  /**
   * Represents various ways sweep transaction detection could fail.
   */
  sealed class TransactionError : Error() {
    /**
     * If transaction has more than one output.
     */
    data class MultipleOutputsError(
      override val message: String = "Multiple Outputs found",
    ) : TransactionError()

    /**
     * If the wallet is not actually empty and has some unspent outputs.
     */
    data class WalletNotEmptyError(
      override val message: String = "Wallet is not empty",
    ) : TransactionError()
  }
}
