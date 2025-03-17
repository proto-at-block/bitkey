package build.wallet.pricechart

import build.wallet.bitkey.f8e.AccountId
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface ChartDataFetcherService {
  /**
   * Retrieves chart data from F8e.
   */
  suspend fun getChartData(
    accountId: AccountId,
    chartHistory: ChartHistory,
    maxPricePoints: Int? = null,
  ): Result<List<DataPoint>, NetworkingError>
}
