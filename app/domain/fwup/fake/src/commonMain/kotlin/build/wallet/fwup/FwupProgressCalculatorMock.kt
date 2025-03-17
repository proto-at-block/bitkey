package build.wallet.fwup

class FwupProgressCalculatorMock : FwupProgressCalculator {
  override fun calculateProgress(
    sequenceId: UInt,
    finalSequenceId: UInt,
  ): Float {
    return 0.5f
  }
}
