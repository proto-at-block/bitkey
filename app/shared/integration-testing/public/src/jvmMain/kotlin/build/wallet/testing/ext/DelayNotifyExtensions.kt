package build.wallet.testing.ext

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.recovery.Recovery
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.recover
import io.kotest.assertions.fail
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * Completes the delay period on f8e for the current account.
 */
suspend fun AppTester.completeRecoveryDelayPeriodOnF8e() {
  val accountId = getFullAccountId()
  val config = defaultAccountConfigService.activeOrDefaultConfig().value
  updateDelayNotifyPeriodForTestingApi
    .updateDelayNotifyPeriodForTesting(
      config.f8eEnvironment,
      accountId,
      delayNotifyDuration = 0.seconds
    )
    .getOrThrow()
}

private suspend fun AppTester.getFullAccountId(): FullAccountId {
  try {
    return getActiveFullAccount().accountId
  } catch (e: CancellationException) {
    // Cancellations are expected, rethrow to ensure structured cancellation.
    throw e
  } catch (_: Exception) {
    val recovery = recoveryDao.activeRecovery().first().recover { null }.value
    if (recovery is Recovery.StillRecovering) {
      return recovery.fullAccountId
    }
  }

  fail("No active full account or recovery found")
}
