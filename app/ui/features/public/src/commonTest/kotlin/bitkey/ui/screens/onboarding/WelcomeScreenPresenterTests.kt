package bitkey.ui.screens.onboarding

import bitkey.ui.framework.test
import bitkey.ui.screens.demo.DemoModeDisabledScreen
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.robots.click
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WelcomeScreenPresenterTests : FunSpec({
  fun presenter(appVariant: AppVariant = AppVariant.Customer) = WelcomeScreenPresenter(appVariant)

  test("show demo mode in Customer app on logo click") {
    presenter(AppVariant.Customer).test(WelcomeScreen) { navigator ->
      awaitBody<ChooseAccountAccessModel> {
        onLogoClick()
      }

      navigator.goToCalls.awaitItem().shouldBe(DemoModeDisabledScreen)
    }
  }

  test("show debug menu in Team app on logo click") {
    presenter(AppVariant.Team).test(WelcomeScreen) { navigator ->
      awaitBody<ChooseAccountAccessModel> {
        onLogoClick()
      }

      // TODO: show and assert debug menu screen
    }
  }

  test("show debug menu in Development app on logo click") {
    presenter(AppVariant.Development).test(WelcomeScreen) { navigator ->
      awaitBody<ChooseAccountAccessModel> {
        onLogoClick()
      }

      // TODO: show and assert debug menu screen
    }
  }

  test("show debug menu in Alpha app on logo click") {
    presenter(AppVariant.Alpha).test(WelcomeScreen) { navigator ->
      awaitBody<ChooseAccountAccessModel> {
        onLogoClick()
      }

      // TODO: show and assert debug menu screen
    }
  }

  test("show debug menu in Emergency app on logo click") {
    presenter(AppVariant.Emergency).test(WelcomeScreen) { navigator ->
      awaitBody<ChooseAccountAccessModel> {
        onLogoClick()
      }

      // TODO: show and assert debug menu screen
    }
  }

  test("go to Set up new wallet screen") {
    presenter().test(WelcomeScreen) { navigator ->
      awaitBody<ChooseAccountAccessModel> {
        buttons[0].click()
      }

      // TODO: show and assert Set up new wallet screen
    }
  }

  test("go to More Options screen") {
    presenter().test(WelcomeScreen) { navigator ->
      awaitBody<ChooseAccountAccessModel> {
        buttons[1].click()
      }

      navigator.goToCalls.awaitItem().shouldBe(AccountAccessOptionsScreen)
    }
  }
})
