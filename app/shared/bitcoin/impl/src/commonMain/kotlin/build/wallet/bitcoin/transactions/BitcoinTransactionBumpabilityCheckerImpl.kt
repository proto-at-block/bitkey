package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkUtxo
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import kotlinx.collections.immutable.ImmutableList

class BitcoinTransactionBumpabilityCheckerImpl(
  private val sweepChecker: BitcoinTransactionSweepChecker,
) : BitcoinTransactionBumpabilityChecker {
  override fun isBumpable(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: ImmutableList<BdkUtxo>,
  ): Boolean {
    return !sweepChecker.isSweep(transaction, walletUnspentOutputs) &&
      transaction.transactionType == Outgoing &&
      transaction.confirmationStatus == Pending
  }
}
