package build.wallet.integration.statemachine.create

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_ACCOUNT_SERVER_KEYS_LOADING
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.*
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormBodyModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.input.EmailInputScreenModel
import build.wallet.statemachine.core.input.VerificationCodeInputFormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.notifications.NotificationPreferenceFormBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickSetUpNewWalletButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class CreateAndOnboardFullAccountFunctionalTests : FunSpec() {
  lateinit var app: AppTester

  init {
    beforeEach {
      app = launchNewApp()

      // Set push notifications to authorized to enable us to successfully advance through
      // the notifications step in onboarding.
      app.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.Authorized
      )
    }

    test("happy path through create and then onboard and activate keybox") {
      app.appUiStateMachine.test(
        Unit,
        useVirtualTime = false,
        testTimeout = 60.seconds,
        turbineTimeout = 20.seconds
      ) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(
          listOf(CloudBackup, NotificationPreferences)
        )
        awaitUntilScreenWithBody<LoadingSuccessBodyModel>(LOADING_SAVING_KEYBOX) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilScreenWithBody<MoneyHomeBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("close and reopen app to cloud backup onboard step") {
      testCloseAndReopenAppToOnboardingScreen<FormBodyModel>(
        stepsToAdvance = emptyList(),
        screenIdExpectation = SAVE_CLOUD_BACKUP_INSTRUCTIONS
      )
    }

    test("close and reopen app to notification pref onboard step") {
      testCloseAndReopenAppToOnboardingScreen<FormBodyModel>(
        stepsToAdvance = listOf(CloudBackup),
        screenIdExpectation = NOTIFICATION_PREFERENCES_SETUP
      )
    }
  }

  private suspend inline fun <reified T : BodyModel> testCloseAndReopenAppToOnboardingScreen(
    stepsToAdvance: List<OnboardingKeyboxStep>,
    screenIdExpectation: EventTrackerScreenId,
  ) {
    app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(stepsToAdvance)
      awaitUntilScreenWithBody<T>(screenIdExpectation)
      cancelAndIgnoreRemainingEvents()
    }

    val newApp = app.relaunchApp()
    newApp.appUiStateMachine.test(Unit, useVirtualTime = false) {
      awaitUntilScreenWithBody<T>(screenIdExpectation)
      cancelAndIgnoreRemainingEvents()
    }
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCreateKeyboxScreens() {
  awaitUntilScreenWithBody<ChooseAccountAccessModel>()
    .clickSetUpNewWalletButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(
    HW_ACTIVATION_INSTRUCTIONS,
    expectedBodyContentMatch = {
      // Wait until loading state is cleared from the primary button
      !it.primaryButton.isLoading
    }
  ).clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(NEW_ACCOUNT_SERVER_KEYS_LOADING) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughOnboardKeyboxScreens(
  stepsToAdvance: List<OnboardingKeyboxStep>,
  isCloudBackupSkipSignIn: Boolean = false,
) {
  stepsToAdvance.forEach { step ->
    when (step) {
      CloudBackup -> {
        if (isCloudBackupSkipSignIn) {
          awaitUntilScreenWithBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING) {
            state.shouldBe(LoadingSuccessBodyModel.State.Loading)
          }
        } else {
          awaitUntilScreenWithBody<SaveBackupInstructionsBodyModel>()
            .onBackupClick()
          awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
            .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
        }
      }

      NotificationPreferences -> advanceThroughOnboardingNotificationSetupScreens()
    }
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughOnboardingNotificationSetupScreens() {
  awaitUntilScreenWithBody<RecoveryChannelsSetupFormBodyModel>()
    .emailItem.onClick.shouldNotBeNull().invoke()
  advanceThroughEmailScreensEnterAndVerify()

  // Check that the email touchpoint has propagated back to the state machine
  // It propagates through the [notificationTouchpointDao], but if it hasn't been
  // received before returning to this screen, will cause a recomposition and the
  // continue button won't progress forward.
  awaitUntilScreenWithBody<RecoveryChannelsSetupFormBodyModel>(
    expectedBodyContentMatch = {
      it.emailItem.state == RecoveryChannelsSetupFormItemModel.State.Completed
    }
  ) {
    continueOnClick()
  }

  // Accept the TOS
  awaitUntilScreenWithBody<NotificationPreferenceFormBodyModel>()
    .tosInfo.shouldNotBeNull().onTermsAgreeToggle(true)

  awaitUntilScreenWithBody<NotificationPreferenceFormBodyModel>()
    .continueOnClick()
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughEmailScreensEnterAndVerify() {
  awaitUntilScreenWithBody<EmailInputScreenModel>()
    .onValueChange("integration-test@wallet.build") // Fake email
  awaitUntilScreenWithBody<EmailInputScreenModel>(
    expectedBodyContentMatch = { it.primaryButton.isEnabled }
  ) {
    clickPrimaryButton()
  }
  awaitUntilScreenWithBody<VerificationCodeInputFormBodyModel>()
    .onValueChange("123456") // This code always works for Test Accounts
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(EMAIL_INPUT_SENDING_CODE_TO_SERVER)
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER)
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL)
}
