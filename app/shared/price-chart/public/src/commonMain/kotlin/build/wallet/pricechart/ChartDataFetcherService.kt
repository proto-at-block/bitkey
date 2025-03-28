package build.wallet.pricechart

import com.github.michaelbull.result.Result

interface ChartDataFetcherService {
  /**
   * Retrieves chart data from F8e.
   */
  suspend fun getChartData(
    range: ChartRange,
    maxPricePoints: Int? = null,
  ): Result<List<DataPoint>, Error>
}
