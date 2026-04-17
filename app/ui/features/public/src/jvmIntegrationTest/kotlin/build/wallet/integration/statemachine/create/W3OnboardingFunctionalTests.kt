package build.wallet.integration.statemachine.create

import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP
import build.wallet.feature.setFlagValue
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryNotificationsSetupFormBodyModel
import build.wallet.statemachine.core.input.PhoneNumberInputBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.notifications.NotificationPreferenceFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.robots.clickSetUpNewWalletButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.HardwareCoverageMode
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for the W3 onboarding notification flow.
 *
 * The hub ("Set up critical alerts") screen has been removed. The flow is now always sequential:
 * Email → SMS (if shown) → Push notification setup → Transaction preferences
 *
 * Close buttons on intermediate screens are now back buttons that navigate to the previous step.
 */
class W3OnboardingFunctionalTests : FunSpec({

  suspend fun AppTester.prepareApp(): AppTester {
    return apply {
      pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.Authorized
      )
      accountConfigService.setHardwareType(HardwareType.W3).getOrThrow()
    }
  }

  context("Sequential flow - email first, always") {
    test("email success automatically advances to SMS when SMS visible") {
      val app = launchNewApp()
      app.prepareApp()
      app.usSmsFeatureFlag.setFlagValue(true) // Ensure SMS is shown

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens(HardwareCoverageMode.W3Private)
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        advanceThroughEmailScreensEnterAndVerify(hardwareType = HardwareType.W3)

        // Should automatically advance to SMS (not return to hub)
        awaitUntilBody<PhoneNumberInputBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("full sequential flow: email -> SMS -> push -> transactions") {
      val app = launchNewApp()
      app.prepareApp()
      app.usSmsFeatureFlag.setFlagValue(true)

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens(HardwareCoverageMode.W3Private)
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        advanceThroughEmailScreensEnterAndVerify(hardwareType = HardwareType.W3)

        // Should automatically advance to SMS
        advanceThroughSmsScreensEnterAndVerify(hardwareType = HardwareType.W3)

        // After SMS, push permission is already granted (mock returns Authorized)
        // So we skip the push setup page and go directly to notification preferences
        awaitUntilBody<NotificationPreferenceFormBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("email success skips SMS and shows push page when SMS is hidden") {
      val app = launchNewApp()
      app.prepareApp()
      app.usSmsFeatureFlag.setFlagValue(false) // SMS hidden

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens(HardwareCoverageMode.W3Private)
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        advanceThroughEmailScreensEnterAndVerify(hardwareType = HardwareType.W3)

        // Push is already authorized in the mock so should skip directly to transactions
        awaitUntilBody<NotificationPreferenceFormBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Back navigation in sequential flow") {
    test("SMS skip button advances to push setup page") {
      val app = launchNewApp()
      app.prepareApp()
      app.usSmsFeatureFlag.setFlagValue(true) // Ensure SMS is visible
      // Use NotDetermined push status so push setup page is shown
      app.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.NotDetermined
      )

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens(HardwareCoverageMode.W3Private)
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        advanceThroughEmailScreensEnterAndVerify(hardwareType = HardwareType.W3)

        // Should advance to SMS - click skip secondary button
        awaitUntilBody<PhoneNumberInputBodyModel> {
          secondaryButton.shouldNotBeNull().text.shouldBe("Skip")
          clickSecondaryButton()
        }

        // Should show push notification setup page (back button, not X)
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("push setup back button returns to SMS when SMS shown") {
      val app = launchNewApp()
      app.prepareApp()
      app.usSmsFeatureFlag.setFlagValue(true)
      app.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.NotDetermined
      )

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens(HardwareCoverageMode.W3Private)
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        advanceThroughEmailScreensEnterAndVerify(hardwareType = HardwareType.W3)

        advanceThroughSmsScreensEnterAndVerify(hardwareType = HardwareType.W3)

        // Should show push setup page with back button
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          // Tap back
          onNavigateBack()
        }

        // Should return to SMS
        awaitUntilBody<PhoneNumberInputBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("push setup skip button advances to transactions") {
      val app = launchNewApp()
      app.prepareApp()
      app.usSmsFeatureFlag.setFlagValue(false) // Skip SMS
      app.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.NotDetermined
      )

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens(HardwareCoverageMode.W3Private)
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        advanceThroughEmailScreensEnterAndVerify(hardwareType = HardwareType.W3)

        // Push setup page shown (push not determined)
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          clickSecondaryButton() // "Skip"
        }

        // Should advance to transactions
        awaitUntilBody<NotificationPreferenceFormBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Back navigation during W3 pairing") {
    test("back from activation instructions V2 returns to choose account access") {
      val app = launchNewApp()
      app.prepareApp()

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        awaitUntilBody<ChooseAccountAccessModel>()
          .clickSetUpNewWalletButton()

        val activationScreen = awaitUntilBody<PairNewHardwareBodyModel>(
          matching = { !it.primaryButton.isLoading }
        )
        activationScreen.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId.shouldBe(HW_ACTIVATION_INSTRUCTIONS_V2)

        activationScreen.onBack.shouldNotBeNull().invoke()

        awaitUntilBody<ChooseAccountAccessModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("back from Finished On Your Device returns to choose account access") {
      val app = launchNewApp()
      app.prepareApp()

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        awaitUntilBody<ChooseAccountAccessModel>()
          .clickSetUpNewWalletButton()

        val activationScreen = awaitUntilBody<PairNewHardwareBodyModel>(
          matching = { !it.primaryButton.isLoading }
        )
        activationScreen.clickPrimaryButton()

        val finishedScreen = awaitUntilBody<CompleteTwoTapBodyModel>(HW_COMPLETE_TWO_TAP)
        finishedScreen.onBack.shouldNotBeNull().invoke()

        awaitUntilBody<ChooseAccountAccessModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Push notification handling") {
    test("push already authorized skips fullscreen page and goes to transactions") {
      val app = launchNewApp()
      app.prepareApp() // Sets push to Authorized
      app.usSmsFeatureFlag.setFlagValue(true)

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens(HardwareCoverageMode.W3Private)
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        advanceThroughEmailScreensEnterAndVerify(hardwareType = HardwareType.W3)
        advanceThroughSmsScreensEnterAndVerify(hardwareType = HardwareType.W3)

        // After SMS, push is already authorized so should skip to transactions
        awaitUntilBody<NotificationPreferenceFormBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }
  }
})
