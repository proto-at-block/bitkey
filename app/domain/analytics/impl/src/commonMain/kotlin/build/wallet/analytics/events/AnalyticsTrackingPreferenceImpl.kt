package build.wallet.analytics.events

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.config.AppVariant
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.*

/**
 * Determines how analytics tracking preference behaves for a given app variant.
 *
 * [AlwaysEnabled] - analytics is always on and cannot be changed by the user.
 * [Mutable] - analytics can be toggled via the debug menu, with a configurable [Mutable.default].
 */
private sealed interface AnalyticsBehavior {
  data object AlwaysEnabled : AnalyticsBehavior

  data class Mutable(val default: Boolean) : AnalyticsBehavior
}

private fun AppVariant.analyticsBehavior(): AnalyticsBehavior =
  when (this) {
    AppVariant.Customer, AppVariant.Emergency -> AnalyticsBehavior.AlwaysEnabled
    AppVariant.Team -> AnalyticsBehavior.Mutable(default = true)
    AppVariant.Development, AppVariant.Alpha -> AnalyticsBehavior.Mutable(default = false)
  }

@BitkeyInject(AppScope::class)
class AnalyticsTrackingPreferenceImpl(
  appVariant: AppVariant,
  private val databaseProvider: BitkeyDatabaseProvider,
) : AnalyticsTrackingPreference {
  private val behavior = appVariant.analyticsBehavior()

  override suspend fun get(): Boolean {
    return when (behavior) {
      is AnalyticsBehavior.AlwaysEnabled -> true
      is AnalyticsBehavior.Mutable ->
        databaseProvider.debugDatabase()
          .analyticsTrackingDebugConfigQueries
          .getConfig()
          .awaitAsOneOrNullResult()
          .get()
          ?.enabled
          ?: behavior.default
    }
  }

  override suspend fun set(enabled: Boolean) {
    if (behavior is AnalyticsBehavior.Mutable) {
      databaseProvider.debugDatabase()
        .analyticsTrackingDebugConfigQueries
        .awaitTransaction {
          setConfig(enabled)
        }
        .logFailure { "Failed to set analytics tracking config" }
    }
  }

  override fun isEnabled(): Flow<Boolean> {
    return when (behavior) {
      is AnalyticsBehavior.AlwaysEnabled -> flowOf(true)
      is AnalyticsBehavior.Mutable ->
        flow {
          databaseProvider.debugDatabase()
            .analyticsTrackingDebugConfigQueries
            .getConfig()
            .asFlowOfOneOrNull()
            .map { it.get()?.enabled ?: behavior.default }
            .collect(::emit)
        }
    }
  }
}
