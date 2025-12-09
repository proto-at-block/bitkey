package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkUtxo
import kotlinx.collections.immutable.ImmutableList

data class BitcoinTransactionBumpabilityCheckerFake(
  var isBumpable: Boolean,
) : BitcoinTransactionBumpabilityChecker {
  override fun isBumpable(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: ImmutableList<BdkUtxo>,
  ): Boolean {
    return when (transaction.confirmationStatus) {
      BitcoinTransaction.ConfirmationStatus.Pending -> isBumpable
      is BitcoinTransaction.ConfirmationStatus.Confirmed -> false
    }
  }
}
