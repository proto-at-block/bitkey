package build.wallet.integration.statemachine.create

import build.wallet.feature.setFlagValue
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.robots.clickSetUpNewWalletButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class CreateSoftwareWalletE2eTests : FunSpec({
  lateinit var appTester: AppTester

  beforeEach {
    appTester = launchNewApp()

    // Set push notifications to authorized to enable us to successfully advance through
    // the notifications step in onboarding.
    appTester.app.appComponent.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      PermissionStatus.Authorized
    )

    appTester.app.appComponent.softwareWalletIsEnabledFeatureFlag.setFlagValue(true)
  }

  xtest("create software wallet") {
    appTester.app.appUiStateMachine.test(Unit) {
      awaitUntilScreenWithBody<ChooseAccountAccessModel>()
        .clickSetUpNewWalletButton()

      awaitUntilScreenWithBody<FormBodyModel> {
        val buttons = mainContentList
          .first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel

        buttons.items[0]
          .title
          .shouldBe("Use Bitkey hardware")

        buttons.items[1].run {
          title.shouldBe("Use this device")
          onClick.shouldNotBeNull().invoke()
        }
      }

      awaitUntilScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Creating Software Wallet...")
      }

      // Set up notifications
      advanceThroughOnboardingNotificationSetupScreens()

      awaitUntilScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Success)
        message.shouldBe("Software Wallet Created")
      }
    }
  }
})
