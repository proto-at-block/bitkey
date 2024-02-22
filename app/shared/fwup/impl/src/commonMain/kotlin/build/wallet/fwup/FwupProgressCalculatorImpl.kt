package build.wallet.fwup

import kotlin.math.round

class FwupProgressCalculatorImpl : FwupProgressCalculator {
  override fun calculateProgress(
    sequenceId: UInt,
    finalSequenceId: UInt,
  ): Float {
    if (finalSequenceId == 0U) {
      return 0f
    }
    // Round to percentage with two decimal places and max out at 100.00
    return (round((sequenceId.toFloat() / finalSequenceId.toFloat()) * 10_000) / 100)
      .coerceAtMost(100f)
  }
}
