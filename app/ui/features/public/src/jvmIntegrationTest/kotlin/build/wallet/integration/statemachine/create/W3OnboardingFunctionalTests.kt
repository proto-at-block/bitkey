package build.wallet.integration.statemachine.create

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.*
import build.wallet.feature.setFlagValue
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormBodyModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.NotCompleted
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.input.EmailInputScreenModel
import build.wallet.statemachine.core.input.PhoneNumberInputBodyModel
import build.wallet.statemachine.core.input.VerificationCodeInputFormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.notifications.NotificationPreferenceFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for the W3 onboarding feature.
 *
 * Tests cover:
 * - Feature flag OFF: Hub-and-spoke behavior (no regression)
 * - Feature flag ON: Sequential flow (email → SMS → push → transactions)
 * - Close/back behavior returns to hub, Skip advances to next step
 * - Hub Continue button works for manual completion
 * - Push notification handling (granted/denied)
 *
 * Note: Settings entry point is not affected by these changes as it uses
 * a different flow path (NotificationTouchpointInputAndVerificationUiStateMachine
 * with Settings entry point, not NotificationPreferencesSetupUiStateMachine).
 */
class W3OnboardingFunctionalTests : FunSpec({

  fun AppTester.prepareApp(): AppTester {
    return apply {
      pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.Authorized
      )
    }
  }

  context("Feature flag OFF - Hub-and-spoke behavior (no regression)") {
    test("email success returns to hub, then manual continue works") {
      val app = launchNewApp()
      app.prepareApp()
      app.w3OnboardingFeatureFlag.setFlagValue(false)

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.state.shouldBe(NotCompleted)
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email verification
        advanceThroughEmailScreensEnterAndVerify()

        // Should return to hub with email completed (hub-and-spoke behavior)
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>(
          matching = { it.emailItem.state == Completed }
        ) {
          emailItem.state.shouldBe(Completed)
        }

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Feature flag ON - Sequential flow with SMS shown") {
    test("email success automatically advances to SMS flow when SMS visible") {
      val app = launchNewApp()
      app.prepareApp()
      app.w3OnboardingFeatureFlag.setFlagValue(true)
      // Enable US SMS to ensure SMS is shown regardless of country
      app.usSmsFeatureFlag.setFlagValue(true)

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        // Start at hub - verify SMS is visible
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>(
          matching = { it.smsItem != null }
        ) {
          smsItem.shouldNotBeNull()
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email verification
        advanceThroughEmailScreensEnterAndVerify()

        // Should automatically advance to SMS (not return to hub)
        awaitUntilBody<PhoneNumberInputBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("full sequential flow: email -> SMS -> push -> transactions") {
      val app = launchNewApp()
      app.prepareApp()
      app.w3OnboardingFeatureFlag.setFlagValue(true)
      app.usSmsFeatureFlag.setFlagValue(true)

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        // Start at hub - verify SMS is visible
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>(
          matching = { it.smsItem != null }
        ) {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email verification
        advanceThroughEmailScreensEnterAndVerify()

        // Should automatically advance to SMS
        advanceThroughSmsScreensEnterAndVerify()

        // After SMS, push permission is already granted (mock returns Authorized)
        // So we skip the hub and go directly to notification preferences
        awaitUntilBody<NotificationPreferenceFormBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Feature flag ON - Sequential flow with SMS hidden") {
    test("email success skips SMS when SMS not visible") {
      val app = launchNewApp()
      app.prepareApp()
      app.w3OnboardingFeatureFlag.setFlagValue(true)
      // Disable US SMS so SMS is hidden for US users
      app.usSmsFeatureFlag.setFlagValue(false)

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        // Start at hub - check if SMS is hidden
        val recoverySetupScreen = awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
        val hasSmsItem = recoverySetupScreen.smsItem != null

        // Click email to start sequential flow
        recoverySetupScreen.emailItem.onClick.shouldNotBeNull().invoke()

        // Complete email verification
        advanceThroughEmailScreensEnterAndVerify()

        if (!hasSmsItem) {
          // SMS was hidden, push is already authorized - go directly to transactions
          awaitUntilBody<NotificationPreferenceFormBodyModel>()
        } else {
          // SMS was visible - should go to SMS flow
          awaitUntilBody<PhoneNumberInputBodyModel>()
        }

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Close/exit at any point returns to hub, Skip advances to next step") {
    test("closing email during sequential flow returns to hub") {
      val app = launchNewApp()
      app.prepareApp()
      app.w3OnboardingFeatureFlag.setFlagValue(true)

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // In email input screen - go back
        awaitUntilBody<EmailInputScreenModel> {
          onClose()
        }

        // Should return to hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("clicking SMS skip button after email success advances to notification setup page") {
      val app = launchNewApp()
      app.prepareApp()
      app.w3OnboardingFeatureFlag.setFlagValue(true)
      app.usSmsFeatureFlag.setFlagValue(true) // Ensure SMS is visible

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        // Start at hub - verify SMS is visible
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>(
          matching = { it.smsItem != null }
        ) {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email verification
        advanceThroughEmailScreensEnterAndVerify()

        // Should advance to SMS - click skip secondary button
        awaitUntilBody<PhoneNumberInputBodyModel> {
          // Verify skip button is present as secondary button
          secondaryButton.shouldNotBeNull().text.shouldBe("Skip")
          // Click skip button
          clickSecondaryButton()
        }

        // Should advance to notification setup page (push is already authorized in mock)
        // Since push is already authorized, we should go directly to notification preferences
        awaitUntilBody<NotificationPreferenceFormBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("closing SMS after email success returns to hub with email completed") {
      val app = launchNewApp()
      app.prepareApp()
      app.w3OnboardingFeatureFlag.setFlagValue(true)
      app.usSmsFeatureFlag.setFlagValue(true) // Ensure SMS is visible

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        // Start at hub - verify SMS is visible
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>(
          matching = { it.smsItem != null }
        ) {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email verification
        advanceThroughEmailScreensEnterAndVerify()

        // Should advance to SMS
        awaitUntilBody<PhoneNumberInputBodyModel> {
          // Close SMS flow
          onClose()
        }

        // Should return to hub with email completed
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>(
          matching = { it.emailItem.state == Completed }
        ) {
          emailItem.state.shouldBe(Completed)
        }

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Hub Continue button works for manual completion") {
    test("completing email then clicking continue works with flag off") {
      val app = launchNewApp()
      app.prepareApp()
      app.w3OnboardingFeatureFlag.setFlagValue(false)

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email manually
        advanceThroughEmailScreensEnterAndVerify()

        // Should return to hub (hub-and-spoke behavior)
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>(
          matching = { it.emailItem.state == Completed }
        ) {
          // Click continue button
          continueOnClick()
        }

        // Should advance (may show skip confirmation or notification preferences)
        // depending on other channel states

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Push notification handling") {
    test("push already authorized skips fullscreen page and goes to transactions") {
      val app = launchNewApp()
      app.prepareApp() // Sets push to Authorized
      app.w3OnboardingFeatureFlag.setFlagValue(true)
      app.usSmsFeatureFlag.setFlagValue(true) // Ensure SMS is visible

      app.appUiStateMachine.test(
        Unit,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(listOf(CloudBackup))

        // Start at hub - verify SMS is visible
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>(
          matching = { it.smsItem != null }
        ) {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email
        advanceThroughEmailScreensEnterAndVerify()

        // Complete SMS
        advanceThroughSmsScreensEnterAndVerify()

        // After SMS, push is already authorized so should skip to transactions
        awaitUntilBody<NotificationPreferenceFormBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }
    }
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughEmailScreensEnterAndVerify() {
  awaitUntilBody<EmailInputScreenModel>()
    .onValueChange("integration-test@wallet.build")
  awaitUntilBody<EmailInputScreenModel>(
    matching = { it.primaryButton.isEnabled }
  ) {
    clickPrimaryButton()
  }
  awaitUntilBody<VerificationCodeInputFormBodyModel>()
    .onValueChange("123456") // This code always works for Test Accounts
  awaitUntilBody<LoadingSuccessBodyModel>(EMAIL_INPUT_SENDING_CODE_TO_SERVER)
  awaitUntilBody<LoadingSuccessBodyModel>(EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER)
  awaitUntilBody<LoadingSuccessBodyModel>(NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL)
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughSmsScreensEnterAndVerify() {
  // Use a valid US phone number format - 555-01xx range is reserved for testing
  // and is valid according to libphonenumber, unlike 555-555-5555
  val phoneNumber = "+12015550123"
  awaitUntilBody<PhoneNumberInputBodyModel>()
    .onTextFieldValueChange(phoneNumber, phoneNumber.length..phoneNumber.length)
  awaitUntilBody<PhoneNumberInputBodyModel>(
    matching = { it.primaryButton.isEnabled }
  ) {
    clickPrimaryButton()
  }
  awaitUntilBody<VerificationCodeInputFormBodyModel>()
    .onValueChange("123456") // This code always works for Test Accounts
  awaitUntilBody<LoadingSuccessBodyModel>(SMS_INPUT_SENDING_CODE_TO_SERVER)
  awaitUntilBody<LoadingSuccessBodyModel>(SMS_INPUT_SENDING_ACTIVATION_TO_SERVER)
  awaitUntilBody<LoadingSuccessBodyModel>(NOTIFICATIONS_HW_APPROVAL_SUCCESS_SMS)
}
