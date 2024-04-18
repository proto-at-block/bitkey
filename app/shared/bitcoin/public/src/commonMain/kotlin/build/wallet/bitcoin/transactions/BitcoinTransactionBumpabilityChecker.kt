package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkUtxo
import kotlinx.collections.immutable.ImmutableList

/**
 * Determines if transaction can be fee bumped.
 *
 * The criteria enforced here is not necessarily something inherent to the nature of the bitcoin
 * protocol, and could include conditions to meet product needs.
 */
interface BitcoinTransactionBumpabilityChecker {
  /**
   * Checks if a transaction can be fee bumped.
   */
  fun isBumpable(
    transaction: BitcoinTransaction,
    walletUnspentOutputs: ImmutableList<BdkUtxo>,
  ): Boolean
}
