package build.wallet.pricechart

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface ChartDataFetcherService {
  /**
   * Retrieves chart data from F8e.
   */
  suspend fun getChartData(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    chartHistory: ChartHistory,
  ): Result<List<DataPoint>, NetworkingError>
}
