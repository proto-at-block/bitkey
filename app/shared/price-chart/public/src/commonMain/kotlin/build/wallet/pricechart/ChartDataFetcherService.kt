package build.wallet.pricechart

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

interface ChartDataFetcherService {
  /**
   * Retrieves chart data from F8e.
   *
   * When both [accountId] and [f8eEnvironment] are provided, they are used directly
   * instead of resolving the active full account. If either value is omitted, the
   * active full account is resolved and used for both values.
   */
  suspend fun getChartData(
    range: ChartRange,
    maxPricePoints: Int? = null,
    accountId: AccountId? = null,
    f8eEnvironment: F8eEnvironment? = null,
  ): Result<List<DataPoint>, Error>
}
