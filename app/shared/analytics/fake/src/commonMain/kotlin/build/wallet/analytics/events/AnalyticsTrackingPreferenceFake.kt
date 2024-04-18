package build.wallet.analytics.events

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class AnalyticsTrackingPreferenceFake(private val defaultValue: Boolean = true) : AnalyticsTrackingPreference {
  private val isEnabledFlow = MutableStateFlow(defaultValue)

  override suspend fun get(): Boolean {
    return isEnabledFlow.value
  }

  override suspend fun set(enabled: Boolean) {
    isEnabledFlow.value = enabled
  }

  override fun isEnabled(): Flow<Boolean> {
    return isEnabledFlow
  }

  fun clear() {
    isEnabledFlow.value = defaultValue
  }
}
