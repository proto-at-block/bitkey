package build.wallet.analytics.events

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.logging.logFailure
import build.wallet.platform.config.AppVariant
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.*

class AnalyticsTrackingPreferenceImpl(
  private val appVariant: AppVariant,
  private val databaseProvider: BitkeyDatabaseProvider,
) : AnalyticsTrackingPreference {
  override suspend fun get(): Boolean {
    return if (appVariant == AppVariant.Customer || appVariant == AppVariant.Emergency) {
      true
    } else {
      databaseProvider.debugDatabase()
        .analyticsTrackingDebugConfigQueries
        .getConfig()
        .awaitAsOneOrNullResult()
        .get()
        ?.enabled
        ?: false
    }
  }

  override suspend fun set(enabled: Boolean) {
    if (appVariant == AppVariant.Customer || appVariant == AppVariant.Emergency) {
      // Do nothing
    } else {
      databaseProvider.debugDatabase()
        .analyticsTrackingDebugConfigQueries
        .awaitTransaction {
          setConfig(enabled)
        }
        .logFailure { "Failed to set analytics tracking config" }
    }
  }

  override fun isEnabled(): Flow<Boolean> {
    return if (appVariant == AppVariant.Customer) {
      flowOf(true)
    } else {
      flow {
        databaseProvider.debugDatabase()
          .analyticsTrackingDebugConfigQueries
          .getConfig()
          .asFlowOfOneOrNull()
          .map { it.get()?.enabled ?: false }
          .collect(::emit)
      }
    }
  }
}
