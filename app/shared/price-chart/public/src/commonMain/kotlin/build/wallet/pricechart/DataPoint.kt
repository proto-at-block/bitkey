package build.wallet.pricechart

/**
 * An x,y data point used for [build.wallet.pricechart.ui.PriceChart]
 * where [Pair.first] is x and [Pair.second] is y.
 */
interface DataPoint {
  val x: Long
  val y: Double

  companion object {
    operator fun invoke(
      first: Long,
      second: Double,
    ): DataPoint {
      return object : DataPoint {
        override val x: Long = first
        override val y: Double = second
      }
    }
  }
}
