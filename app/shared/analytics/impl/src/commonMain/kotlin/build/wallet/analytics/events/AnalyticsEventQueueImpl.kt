package build.wallet.analytics.events

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

class AnalyticsEventQueueImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : AnalyticsEventQueue {
  override suspend fun append(item: QueueAnalyticsEvent): Result<Unit, Error> {
    return databaseProvider.database().awaitTransaction {
      analyticsEventDatabaseQueries.append(event = item.event, f8eEnvironment = item.f8eEnvironment)
    }
  }

  override suspend fun take(num: Int): Result<List<QueueAnalyticsEvent>, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }

    return databaseProvider.database()
      .analyticsEventDatabaseQueries
      .take(num.toLong())
      .awaitAsListResult()
      .map {
        it.map { event ->
          QueueAnalyticsEvent(event = event.event, f8eEnvironment = event.f8eEnvironment)
        }
      }
  }

  override suspend fun removeFirst(num: Int): Result<Unit, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }

    return databaseProvider.database().awaitTransaction {
      analyticsEventDatabaseQueries.removeFirst(num.toLong())
    }
  }
}
