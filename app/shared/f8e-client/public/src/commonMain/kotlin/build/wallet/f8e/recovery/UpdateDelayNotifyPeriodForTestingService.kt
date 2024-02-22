package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result
import kotlin.time.Duration

/**
 * Service for skipping delay & notify for testing purposes.
 */
interface UpdateDelayNotifyPeriodForTestingService {
  suspend fun updateDelayNotifyPeriodForTesting(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    delayNotifyDuration: Duration,
  ): Result<Unit, NetworkingError>
}
