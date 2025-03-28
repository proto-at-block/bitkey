package build.wallet.integration.statemachine.create

import build.wallet.feature.setFlagValue
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.robots.clickSetUpNewWalletButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class CreateSoftwareWalletE2eTests : FunSpec({

  suspend fun TestScope.launchAndPrepareApp(): AppTester {
    return launchNewApp().apply {
      // Set push notifications to authorized to enable us to successfully advance through
      // the notifications step in onboarding.
      pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.Authorized
      )
      softwareWalletIsEnabledFeatureFlag.setFlagValue(true)
    }
  }

  test("create software wallet") {
    val app = launchAndPrepareApp()
    app.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 20.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickSetUpNewWalletButton()

      awaitUntilBody<FormBodyModel> {
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

      awaitUntilBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Creating Software Wallet...")
      }

      // Set up notifications
      advanceThroughOnboardingNotificationSetupScreens()

      awaitUntilBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Success)
        message.shouldBe("Software Wallet Created")
      }
      cancelAndIgnoreRemainingEvents()
    }
  }
})
