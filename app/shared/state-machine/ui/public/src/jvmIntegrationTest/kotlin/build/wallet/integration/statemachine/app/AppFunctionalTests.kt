package build.wallet.integration.statemachine.app

import build.wallet.bdk.BdkBlockchainFactoryImpl
import build.wallet.bdk.bindings.BdkBlockchain
import build.wallet.bdk.bindings.BdkBlockchainConfig
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkResult
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ToxiProxyService
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import eu.rekawek.toxiproxy.model.ToxicDirection
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class AppFunctionalTests : FunSpec({

  lateinit var toxiProxyService: ToxiProxyService

  beforeTest {
    toxiProxyService = ToxiProxyService()
  }

  afterTest {
    toxiProxyService.reset()
  }

  test("App re-launches with no access to BDK or F8e") {
    val appTester = launchNewApp()

    appTester.onboardFullAccountWithFakeHardware()

    val relaunchedAppTester =
      appTester.relaunchApp(
        bdkBlockchainFactory = UnreachableBdkBlockchainFactory(),
        f8eEnvironment = F8eEnvironment.Custom("unreachable")
      )

    relaunchedAppTester.app.appUiStateMachine.test(Unit) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  // Disabled because we are now parallelizing tests and this test changes global state.
  // Specifically it uses ToxiProxy to break the connection to f8e.
  // See https://linear.app/squareup/issue/BKR-1034
  xtest("BKR-1034 App re-launches with limited access to BDK or F8e") {
    val appTester = launchNewApp()

    appTester.onboardFullAccountWithFakeHardware()

    // Limit responses from fromagerie
    toxiProxyService.fromagerie.toxics()
      .limitData("lose_response", ToxicDirection.DOWNSTREAM, 0)
    val bdkBlockingDelay =
      async {
        delay(5.seconds) // More than the turbine test timeout
      }
    val relaunchedAppTester =
      appTester.relaunchApp(
        bdkBlockchainFactory =
          BlockingBdkBlockchainFactory(
            blockingDelay = { bdkBlockingDelay.await() }
          )
      )

    relaunchedAppTester.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
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
