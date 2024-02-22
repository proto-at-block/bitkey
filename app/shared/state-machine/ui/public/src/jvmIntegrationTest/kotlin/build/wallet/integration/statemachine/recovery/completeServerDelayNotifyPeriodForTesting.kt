package build.wallet.integration.statemachine.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.recovery.UpdateDelayNotifyPeriodForTestingServiceImpl
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.testing.AppTester
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.recover
import io.kotest.assertions.fail
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

suspend fun AppTester.completeServerDelayNotifyPeriodForTesting(f8eEnvironment: F8eEnvironment) {
  UpdateDelayNotifyPeriodForTestingServiceImpl(app.appComponent.f8eHttpClient)
    .updateDelayNotifyPeriodForTesting(f8eEnvironment, getFullAccountId(), 0.seconds)
    .getOrThrow()
}

private suspend fun AppTester.getFullAccountId(): FullAccountId {
  val account =
    try {
      getActiveFullAccount()
    } catch (_: IllegalStateException) {
      null
    }
  if (account != null) {
    return account.accountId
  }

  val recovery = app.appComponent.recoveryDao.activeRecovery().first().recover { null }.value
  if (recovery is StillRecovering) {
    return recovery.fullAccountId
  }

  fail("No active keybox or recovery found")
}
