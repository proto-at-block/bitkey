package build.wallet.testing.ext

import app.cash.turbine.test
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.testing.AppTester
import com.github.michaelbull.result.get

suspend fun AppTester.awaitNoActiveRecovery() {
  recoveryStatusService.status().test {
    awaitUntil { it.get() is NoActiveRecovery }
  }
}
