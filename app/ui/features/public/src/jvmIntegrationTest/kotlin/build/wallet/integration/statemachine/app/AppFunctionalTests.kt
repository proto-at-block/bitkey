package build.wallet.integration.statemachine.app

import build.wallet.bdk.bindings.*
import build.wallet.bdk.legacy.BdkBlockchainFactoryImpl
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

class AppFunctionalTests : FunSpec({

  test("App re-launches with no access to BDK or F8e") {
    val app = launchNewApp()

    app.onboardFullAccountWithFakeHardware()

    val relaunchedApp =
      app.relaunchApp(
        bdkBlockchainFactory = UnreachableBdkBlockchainFactory(),
        f8eEnvironment = F8eEnvironment.Custom("unreachable")
      )

    relaunchedApp.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("BKR-1034 App re-launches with limited access to BDK or F8e") {
    val app = launchNewApp()

    app.onboardFullAccountWithFakeHardware()

    // Limit responses from fromagerie
    app.networkingDebugService.setFailF8eRequests(value = true)
    val bdkBlockingDelay =
      async {
        delay(5.seconds) // More than the turbine test timeout
      }
    val relaunchedApp =
      app.relaunchApp(
        bdkBlockchainFactory =
          BlockingBdkBlockchainFactory(
            blockingDelay = { bdkBlockingDelay.await() }
          )
      )

    relaunchedApp.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel>()
      bdkBlockingDelay.cancel()
      cancelAndIgnoreRemainingEvents()
    }
  }
})

class UnreachableBdkBlockchainFactory : BdkBlockchainFactory {
  override fun blockchainBlocking(config: BdkBlockchainConfig): BdkResult<BdkBlockchain> {
    return BdkResult.Err(BdkError.Generic(null, null))
  }
}

class BlockingBdkBlockchainFactory(
  private val factory: BdkBlockchainFactory = BdkBlockchainFactoryImpl(),
  private val blockingDelay: suspend () -> Unit,
) : BdkBlockchainFactory {
  override fun blockchainBlocking(config: BdkBlockchainConfig): BdkResult<BdkBlockchain> {
    runBlocking { blockingDelay() }
    return factory.blockchainBlocking(config)
  }
}
