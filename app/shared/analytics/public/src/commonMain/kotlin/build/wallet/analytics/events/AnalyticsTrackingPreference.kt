package build.wallet.analytics.events

import kotlinx.coroutines.flow.Flow

/**
 * Preference for tracking analytics events.
 */
interface AnalyticsTrackingPreference {
  suspend fun get(): Boolean

  suspend fun set(enabled: Boolean)

  fun isEnabled(): Flow<Boolean>
}
