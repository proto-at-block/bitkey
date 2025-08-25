package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlin.time.Duration

/**
 * F8E client for configuring delay duration for privileged actions in tests
 */
interface ConfigureDelayDurationF8eClient {
  /**
   * Configure the delay duration for a specific privileged action instance
   */
  suspend fun configureDelayDuration(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    privilegedActionId: String,
    delayDuration: Duration,
  ): Result<Unit, NetworkingError>
}
