package build.wallet.debug

import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec

class AppDataDeleterImplComponentTests : FunSpec({

  test("delete app data when has Full Account") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    app.appDataDeleter.deleteAll().shouldBeOk()

    app.appUiStateMachine.testWithVirtualTime(Unit) {
      awaitUntilBody<ChooseAccountAccessModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("delete app state when already in empty state") {
    val app = launchNewApp()
    app.appDataDeleter.deleteAll().shouldBeOk()

    app.appUiStateMachine.testWithVirtualTime(Unit) {
      awaitUntilBody<ChooseAccountAccessModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }
})
