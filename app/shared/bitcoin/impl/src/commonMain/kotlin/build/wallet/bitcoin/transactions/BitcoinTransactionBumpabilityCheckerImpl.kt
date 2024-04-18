package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkUtxo
import kotlinx.collections.immutable.ImmutableList

class BitcoinTransactionBumpabilityCheckerImpl(
  val sweepChecker: BitcoinTransactionSweepChecker,
) : BitcoinTransactionBumpabilityChecker {
  override fun isBumpable(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: ImmutableList<BdkUtxo>,
  ): Boolean {
    return !sweepChecker.isSweep(transaction, walletUnspentOutputs) &&
      !transaction.incoming &&
      transaction.confirmationStatus == BitcoinTransaction.ConfirmationStatus.Pending
  }
}
