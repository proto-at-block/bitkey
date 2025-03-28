package build.wallet.fwup

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlin.math.round

@BitkeyInject(AppScope::class)
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
