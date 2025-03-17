package build.wallet.fwup

interface FwupProgressCalculator {
  /**
   * @return - A float value in the range 0.00...100.00 rounded to 2 significant
   * figures that represents the progress percentage of FWUP.
   */
  fun calculateProgress(
    sequenceId: UInt,
    finalSequenceId: UInt,
  ): Float
}
