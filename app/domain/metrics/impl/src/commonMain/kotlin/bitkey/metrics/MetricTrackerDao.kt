package bitkey.metrics

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.time.Duration

/**
 * Database access for tracked metrics.
 */
@BitkeyInject(AppScope::class)
class MetricTrackerDao(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val clock: Clock,
) {
  /**
   * Returns all metrics that have not been updated since now - [timeout].
   */
  suspend fun getMetricsPastTimeout(timeout: Duration): List<TrackedMetric> {
    return databaseProvider.database()
      .mobileMetricsQueries.expiredMetrics(clock.now().minus(timeout))
      .awaitAsListResult()
      .logFailure { "Failed to read metrics past timeout" }
      .mapOr(emptyList()) { results ->
        results.map {
          TrackedMetric(
            name = MetricName(it.metricName),
            variant = it.variant
          )
        }
      }
  }

  /**
   * Returns all stored metrics.
   */
  val metrics: Flow<List<TrackedMetric>> = flow {
    databaseProvider.database()
      .mobileMetricsQueries
      .getMetrics()
      .asFlowOfList()
      .map { result ->
        result
          .logFailure { "Failed to read mobile metrics from database" }
          .mapOr(emptyList()) { entities ->
            entities.map {
              TrackedMetric(
                name = MetricName(it.metricName),
                variant = it.variant
              )
            }
          }
      }
      .distinctUntilChanged()
      .collect(::emit)
  }

  /**
   * Stores a new metric in the database, returning the current metric of the same name if it exists
   */
  suspend fun storeMetric(trackedMetric: TrackedMetric): Result<TrackedMetric?, Error> {
    return databaseProvider.database().awaitTransactionWithResult {
      // Get existing metric, if one exists
      val existingMetric = mobileMetricsQueries.getByMetricName(trackedMetric.name.name)
        .executeAsOneOrNull()?.let {
          TrackedMetric(
            name = MetricName(it.metricName),
            variant = it.variant
          )
        }

      // Overwrite the stored metric with the latest
      mobileMetricsQueries.insertOrUpdate(
        metricName = trackedMetric.name.name,
        variant = trackedMetric.variant,
        lastUpdated = clock.now()
      )

      // Return existing
      existingMetric
    }
  }

  /**
   * Sets the variant on an existing metric
   */
  suspend fun setVariant(
    metricName: MetricName,
    variant: Variant<*>,
  ): Result<Unit, Error> {
    return databaseProvider.database().awaitTransaction {
      // Get existing metric, if one exists
      val existingMetric = mobileMetricsQueries.getByMetricName(metricName.name)
        .executeAsOneOrNull()?.let {
          TrackedMetric(
            name = MetricName(it.metricName),
            variant = it.variant
          )
        }

      // Best effort
      // TODO this should return an error if existing metric is null
      if (existingMetric != null) {
        mobileMetricsQueries.insertOrUpdate(
          metricName = metricName.name,
          variant = variant.name,
          lastUpdated = clock.now()
        )
      }
    }
  }

  /**
   * Removes the provided [metricName] from the database.
   */
  suspend fun removeByName(metricName: MetricName): Result<TrackedMetric?, Error> {
    return databaseProvider.database().awaitTransactionWithResult {
      // Get existing metric, if one exists
      val existingMetric = mobileMetricsQueries.getByMetricName(metricName.name)
        .executeAsOneOrNull()?.let {
          TrackedMetric(
            name = MetricName(it.metricName),
            variant = it.variant
          )
        }

      // Overwrite the stored metric with the latest
      mobileMetricsQueries.removeByMetricName(metricName.name)

      // Return existing
      existingMetric
    }
  }

  /**
   * Clears all entries in the database.
   */
  suspend fun clear() {
    databaseProvider.database().mobileMetricsQueries.clear()
  }
}
