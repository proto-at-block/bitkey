package build.wallet.recovery.sweep

import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.money.BitcoinMoney
import build.wallet.money.sumOf

/**
 * Represents generated psbts to sweep funds from inactive keysets to the active keyset.
 */
data class Sweep(
  /**
   * All unsigned PSBTs that need to be signed by the customer's appropriate physical factor (app or hardware),
   */
  val unsignedPsbts: Set<SweepPsbt>,
) {
  init {
    val hasPsbts = unsignedPsbts.isNotEmpty()
    require(hasPsbts) { "unsignedPsbts must not be empty" }
  }

  /**
   * Sweep psbts that need to be signed with hardware, if any.
   */
  val psbtsRequiringHwSign: Set<SweepPsbt> =
    unsignedPsbts.filter { it.signingFactor == Hardware }.toSet()

  /**
   * Bitcoin fee amount required to broadcast the sweep transaction
   * for all sweep PSBTs.
   */
  val totalFeeAmount: BitcoinMoney = unsignedPsbts.sumOf { it.psbt.fee }

  /**
   * Total amount of bitcoin to transfer in the sweep transaction.
   */
  val totalTransferAmount: BitcoinMoney = unsignedPsbts.sumOf { it.psbt.amountBtc }
}
