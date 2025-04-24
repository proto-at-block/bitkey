package build.wallet.pricechart

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides APIs to observe the wallet's balance in BTC and Fiat over time.
 */
interface BalanceHistoryService {
  fun observe(range: ChartRange): Flow<Result<List<BalanceAt>, Error>>

  fun clearData()
}
