package build.wallet.bitcoin.fees

import kotlin.math.abs

/**
 * Represents the relative comparison between two fee rates (Augur vs Mempool).
 * Categories are based on percentage difference thresholds:
 * - EQUAL: < 1% difference
 * - SLIGHTLY_*: 1-5% difference
 * - *: 5-10% difference
 * - MUCH_*: 10-20% difference
 * - SIGNIFICANTLY_*: > 20% difference
 *
 * The comparison is from Augur's perspective relative to Mempool.
 * E.g., HIGHER means Augur's fee rate is higher than Mempool's.
 */
internal enum class FeeRateComparison {
  EQUAL,
  SLIGHTLY_HIGHER,
  SLIGHTLY_LOWER,
  HIGHER,
  LOWER,
  MUCH_HIGHER,
  MUCH_LOWER,
  SIGNIFICANTLY_HIGHER,
  SIGNIFICANTLY_LOWER,
  ;

  companion object {
    /**
     * Compares Augur fee rate to Mempool fee rate and returns the comparison category.
     *
     * @param augurFeeRate The fee rate from Augur
     * @param mempoolFeeRate The fee rate from Mempool (baseline for comparison)
     * @return The comparison category from Augur's perspective relative to Mempool
     */
    fun compare(
      augurFeeRate: FeeRate,
      mempoolFeeRate: FeeRate,
    ): FeeRateComparison {
      // Guarantee mempool rate is not 0
      if (mempoolFeeRate.satsPerVByte <= 0f) {
        return if (augurFeeRate.satsPerVByte > 0f) SIGNIFICANTLY_HIGHER else EQUAL
      }

      val percentageDifference =
        (augurFeeRate.satsPerVByte - mempoolFeeRate.satsPerVByte) / mempoolFeeRate.satsPerVByte * 100f
      val absolutePercentage = abs(percentageDifference)

      return when {
        absolutePercentage < 1f -> EQUAL
        absolutePercentage < 5f -> if (percentageDifference > 0) SLIGHTLY_HIGHER else SLIGHTLY_LOWER
        absolutePercentage < 10f -> if (percentageDifference > 0) HIGHER else LOWER
        absolutePercentage < 20f -> if (percentageDifference > 0) MUCH_HIGHER else MUCH_LOWER
        else -> if (percentageDifference > 0) SIGNIFICANTLY_HIGHER else SIGNIFICANTLY_LOWER
      }
    }
  }
}
