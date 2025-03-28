package build.wallet.pricechart

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class ChartRangePreferenceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  appCoroutineScope: CoroutineScope,
) : ChartRangePreference {
  override val selectedRange: StateFlow<ChartRange> = flow {
    databaseProvider.database()
      .chartRangePreferenceQueries
      .getChartRangePreference()
      .asFlowOfOneOrNull()
      .mapNotNull { it.get()?.timeScale }
      .collect(::emit)
  }.stateIn(appCoroutineScope, SharingStarted.Eagerly, ChartRange.DAY)

  override suspend fun get(): Result<ChartRange, DbError> {
    return databaseProvider.database()
      .chartRangePreferenceQueries
      .getChartRangePreference()
      .awaitAsOneOrNullResult()
      .logFailure { "Unable to get chart range entity" }
      .map { (it?.timeScale ?: ChartRange.DAY) }
  }

  override suspend fun set(scale: ChartRange): Result<Unit, DbError> {
    return databaseProvider.database()
      .chartRangePreferenceQueries
      .awaitTransactionWithResult {
        setChartRangePreference(scale)
      }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .chartRangePreferenceQueries
      .awaitTransactionWithResult {
        clear()
      }
  }
}
