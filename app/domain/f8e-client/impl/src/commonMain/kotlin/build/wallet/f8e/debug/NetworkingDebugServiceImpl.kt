package build.wallet.f8e.debug

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.config.AppVariant
import build.wallet.store.KeyValueStoreFactory
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@BitkeyInject(AppScope::class)
class NetworkingDebugServiceImpl(
  private val keyValueStoreFactory: KeyValueStoreFactory,
  appVariant: AppVariant,
) : NetworkingDebugService {
  /**
   * Whether the networking debug config is customer-facing mutable state on this variant.
   *
   * On [AppVariant.Customer] and [AppVariant.Emergency] there is no debug menu, so the
   * config can never be changed from its default. We short-circuit all persistence access
   * on these variants — the persisted value could only ever be the default anyway.
   */
  private val debugConfigMutable = when (appVariant) {
    AppVariant.Customer, AppVariant.Emergency -> false
    AppVariant.Team, AppVariant.Development, AppVariant.Alpha -> true
  }

  private val defaultConfig =
    NetworkingDebugConfig(
      failF8eRequests = false
    )

  private val configState = MutableStateFlow(defaultConfig)

  override val config: StateFlow<NetworkingDebugConfig> = configState.asStateFlow()

  /**
   * Serializes the startup restore in [launchSync] with [setFailF8eRequests] writes so a
   * slow restore read cannot resume after a write and clobber [configState] with stale state.
   */
  private val lock = Mutex()

  override suspend fun setFailF8eRequests(value: Boolean): Result<Unit, Error> {
    if (!debugConfigMutable) {
      // No debug menu on this variant; the config is permanently the default.
      return Ok(Unit)
    }
    return lock.withLock {
      catchingResult {
        store().putBoolean(FAIL_F8E_REQUESTS_KEY, value)
      }
        .onSuccess {
          configState.update { it.copy(failF8eRequests = value) }
        }
        .mapError { Error(it) }
    }
  }

  override suspend fun launchSync() {
    if (!debugConfigMutable) {
      // Config stays at the compile-time default on customer builds.
      return
    }
    // Load the persisted config once; subsequent changes go through
    // [setFailF8eRequests], which updates [configState] directly.
    // A failed read must not crash app startup (this runs as an AppWorker),
    // so fall back to the compile-time default.
    lock.withLock {
      val persistedFailF8eRequests = catchingResult {
        store().getBoolean(
          key = FAIL_F8E_REQUESTS_KEY,
          defaultValue = defaultConfig.failF8eRequests
        )
      }
        .logFailure { "Failed to read networking debug config; using default" }
        .getOr(defaultConfig.failF8eRequests)
      configState.update { it.copy(failF8eRequests = persistedFailF8eRequests) }
    }
  }

  private suspend fun store() = keyValueStoreFactory.getOrCreate(STORE_NAME)

  private companion object {
    const val STORE_NAME = "NetworkingDebugConfigStore"
    const val FAIL_F8E_REQUESTS_KEY = "fail-f8e-requests"
  }
}
