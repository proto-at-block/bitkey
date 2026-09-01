package build.wallet.analytics.events

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.config.AppVariant
import build.wallet.store.KeyValueStoreFactory
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
  private val keyValueStoreFactory: KeyValueStoreFactory,
) : AnalyticsTrackingPreference {
  private val behavior = appVariant.analyticsBehavior()

  /**
   * In-memory state for [isEnabled] reactivity. `null` until first read from the store.
   * The debug menu is the only writer, so write-through via [set] keeps this consistent
   * with the persisted value.
   */
  private val enabledState = MutableStateFlow<Boolean?>(null)

  /**
   * Serializes the first persisted read with [set] writes so a slow initial read
   * cannot resume after a write and clobber [enabledState] with a stale value.
   */
  private val lock = Mutex()

  override suspend fun get(): Boolean {
    return when (val behavior = behavior) {
      is AnalyticsBehavior.AlwaysEnabled -> true
      is AnalyticsBehavior.Mutable -> readPersisted(behavior)
    }
  }

  override suspend fun set(enabled: Boolean) {
    if (behavior is AnalyticsBehavior.Mutable) {
      lock.withLock {
        catchingResult {
          store().putBoolean(ENABLED_KEY, enabled)
        }
          .onSuccess {
            // Only reflect the new value in memory once it is durably persisted,
            // so get()/isEnabled() never report a value that would revert on restart.
            enabledState.value = enabled
          }
          .logFailure { "Failed to set analytics tracking config" }
      }
    }
  }

  override fun isEnabled(): Flow<Boolean> {
    return when (val behavior = behavior) {
      is AnalyticsBehavior.AlwaysEnabled -> flowOf(true)
      is AnalyticsBehavior.Mutable ->
        flow {
          // Populates enabledState under the lock (no-op if already loaded).
          readPersisted(behavior)
          enabledState.collect { enabled ->
            emit(enabled ?: behavior.default)
          }
        }
    }
  }

  private suspend fun readPersisted(behavior: AnalyticsBehavior.Mutable): Boolean {
    // Serialized with [set] so a slow first read can never resume after a write
    // and overwrite [enabledState] with a stale value.
    return enabledState.value
      ?: lock.withLock {
        enabledState.value
          ?: catchingResult {
            store().getBoolean(key = ENABLED_KEY, defaultValue = behavior.default)
          }
            .logFailure { "Failed to read analytics tracking config; using default" }
            .getOr(behavior.default)
            .also { enabledState.value = it }
      }
  }

  private suspend fun store() = keyValueStoreFactory.getOrCreate(STORE_NAME)

  private companion object {
    const val STORE_NAME = "AnalyticsTrackingStore"
    const val ENABLED_KEY = "analytics-tracking-enabled"
  }
}
