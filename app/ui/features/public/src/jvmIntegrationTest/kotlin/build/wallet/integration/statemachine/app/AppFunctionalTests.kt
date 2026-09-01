package build.wallet.integration.statemachine.app

import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME_RELOADING_WALLET_HISTORY_FAILED
import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.sync.ElectrumServer.Custom
import build.wallet.bitcoin.sync.ElectrumServerDetails
import build.wallet.f8e.F8eEnvironment
import build.wallet.feature.flags.setBdk2Enabled
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.settings.full.feedback.FeedbackEventTrackerScreenId.FEEDBACK_FILLING_FORM
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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

  test("BDK2 initial sync failure shows retry and contact support") {
    val app = launchNewApp()
    app.bdk2InitialSyncCompletionDaoFake.markIncompleteByDefault()
    app.bdk2FeatureFlag.setBdk2Enabled(false)
    app.onboardFullAccountWithFakeHardware()
    app.electrumServerSettingProvider.setUserDefinedServer(
      Custom(
        ElectrumServerDetails(
          protocol = "tcp",
          host = "127.0.0.1",
          port = "1"
        )
      )
    )
    app.bdk2FeatureFlag.setBdk2Enabled(true)

    val bdk2App = app.relaunchApp()

    bdk2App.appUiStateMachine.test(Unit) {
      awaitUntilScreenWithBody<FormBodyModel>(
        id = MONEY_HOME_RELOADING_WALLET_HISTORY_FAILED
      ) {
        val form = body as FormBodyModel
        form.header?.headline.shouldBe("We couldn't update your wallet")
        form.primaryButton.shouldNotBeNull().text.shouldBe("Retry")
        form.secondaryButton.shouldNotBeNull().text.shouldBe("Contact support")

        form.primaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilScreenWithBody<FormBodyModel>(
        id = MONEY_HOME_RELOADING_WALLET_HISTORY_FAILED
      ) {
        val form = body as FormBodyModel
        form.secondaryButton.shouldNotBeNull().onClick()
      }

      awaitUntilScreenWithBody<FormBodyModel>(
        id = FEEDBACK_FILLING_FORM
      )
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
