package build.wallet.f8e.debug

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

interface NetworkingDebugConfigRepository {
  /**
   * Emits latest local [NetworkingDebugConfig] value, updated by [launchSync].
   */
  val config: StateFlow<NetworkingDebugConfig>

  /**
   * Update local [NetworkingDebugConfig.failF8eRequests] value.
   */
  suspend fun setFailF8eRequests(value: Boolean): Result<Unit, Error>

  /**
   * Launches a blocking coroutine to continuously sync latest local [NetworkingDebugConfig] value
   * into [config]. This function should be called only once.
   */
  suspend fun launchSync()
}
