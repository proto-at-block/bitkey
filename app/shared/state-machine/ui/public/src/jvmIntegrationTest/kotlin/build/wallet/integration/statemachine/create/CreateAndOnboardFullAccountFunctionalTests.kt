package build.wallet.integration.statemachine.create

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_ACCOUNT_SERVER_KEYS_LOADING
import build.wallet.analytics.events.screen.id.CurrencyEventTrackerScreenId.CURRENCY_PREFERENCE
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.EMAIL_INPUT_ENTERING_CODE
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.EMAIL_INPUT_ENTERING_EMAIL
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.NOTIFICATION_PREFERENCES_SETUP
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.SMS_INPUT_ENTERING_SMS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.setFlagValue
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.CurrencyPreference
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickMainContentListItemAtIndex
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.clickTrailingAccessoryButton
import build.wallet.statemachine.ui.inputTextToMainContentTextInputItem
import build.wallet.statemachine.ui.inputTextToMainContentVerificationCodeInputItem
import build.wallet.testing.AppTester
import build.wallet.testing.launchNewApp
import build.wallet.testing.relaunchApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf

class CreateAndOnboardFullAccountFunctionalTests : FunSpec() {
  lateinit var appTester: AppTester

  init {
    beforeEach {
      appTester = launchNewApp()
      appTester.app.cloudBackupDeleter.delete(cloudServiceProvider())
      appTester.deleteBackupsFromFakeCloud()

      // Set push notifications to authorized to enable us to successfully advance through
      // the notifications step in onboarding.
      appTester.app.appComponent.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.Authorized
      )

      appTester.app.appComponent.multipleFiatCurrencyEnabledFeatureFlag.apply {
        setFlagValue(defaultFlagValue)
      }
    }

    test("happy path through create, onboard and activate keybox") {
      appTester.app.appUiStateMachine.test(Unit) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(
          listOf(CloudBackup, NotificationPreferences)
        )
        awaitUntilScreenWithBody<LoadingBodyModel>(LOADING_SAVING_KEYBOX)
        awaitUntilScreenWithBody<MoneyHomeBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("happy path through create, onboard and activate keybox with currency preference") {
      appTester.app.appComponent.multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
      appTester.app.appUiStateMachine.test(Unit) {
        advanceThroughCreateKeyboxScreens()
        advanceThroughOnboardKeyboxScreens(
          listOf(CloudBackup, NotificationPreferences, CurrencyPreference)
        )
        awaitUntilScreenWithBody<LoadingBodyModel>(LOADING_SAVING_KEYBOX)
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

    test("close and reopen app to currency pref onboard step") {
      appTester.app.appComponent.multipleFiatCurrencyEnabledFeatureFlag.setFlagValue(true)
      testCloseAndReopenAppToOnboardingScreen<FormBodyModel>(
        stepsToAdvance = listOf(CloudBackup, NotificationPreferences),
        screenIdExpectation = CURRENCY_PREFERENCE
      )
    }
  }

  private suspend inline fun <reified T : BodyModel> testCloseAndReopenAppToOnboardingScreen(
    stepsToAdvance: List<OnboardingKeyboxStep>,
    screenIdExpectation: EventTrackerScreenId,
  ) {
    appTester.app.appUiStateMachine.test(Unit) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(stepsToAdvance)
      awaitUntilScreenWithBody<T>(screenIdExpectation)
      cancelAndIgnoreRemainingEvents()
    }

    val newAppTester = appTester.relaunchApp()
    newAppTester.app.appUiStateMachine.test(Unit) {
      awaitUntilScreenWithBody<T>(screenIdExpectation)
      cancelAndIgnoreRemainingEvents()
    }
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCreateKeyboxScreens() {
  awaitUntilScreenWithBody<ChooseAccountAccessModel>()
    .setUpNewWalletButton.onClick()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS, expectedBodyContentMatch = {
    // Wait until loading state is cleared from the primary button
    !it.primaryButton.isLoading
  }).clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<LoadingBodyModel>(NEW_ACCOUNT_SERVER_KEYS_LOADING)
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughOnboardKeyboxScreens(
  stepsToAdvance: List<OnboardingKeyboxStep>,
  isCloudBackupSkipSignIn: Boolean = false,
) {
  stepsToAdvance.forEach { step ->
    when (step) {
      CloudBackup -> {
        if (isCloudBackupSkipSignIn) {
          awaitUntilScreenWithBody<LoadingBodyModel>(CLOUD_SIGN_IN_LOADING)
        } else {
          awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
            .clickPrimaryButton()
          awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
            .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
        }
      }

      NotificationPreferences -> {
        awaitUntilScreenWithBody<FormBodyModel>(NOTIFICATION_PREFERENCES_SETUP)
          .clickMainContentListItemAtIndex(1) // Tap SMS row item
        advanceThroughSmsScreensAndSkip()

        awaitUntilScreenWithBody<FormBodyModel>(NOTIFICATION_PREFERENCES_SETUP)
          .clickMainContentListItemAtIndex(2) // Tap email row item
        advanceThroughEmailScreensEnterAndVerify()

        awaitUntilScreenWithBody<FormBodyModel>(NOTIFICATION_PREFERENCES_SETUP)
      }

      CurrencyPreference -> {
        awaitUntilScreenWithBody<FormBodyModel>(CURRENCY_PREFERENCE)
          .clickPrimaryButton()
      }
    }
  }
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughSmsScreensAndSkip() {
  // To skip SMS, we tap the trailing accessory button which then shows a bottom sheet
  // asking for confirmation, and we confirm it
  awaitUntilScreenWithBody<FormBodyModel>(SMS_INPUT_ENTERING_SMS)
    .clickTrailingAccessoryButton()
  awaitUntil { it.bottomSheetModel != null }.apply {
    bottomSheetModel.shouldNotBeNull().body.shouldBeTypeOf<FormBodyModel>()
      .clickSecondaryButton()
  }
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughEmailScreensEnterAndVerify() {
  awaitUntilScreenWithBody<FormBodyModel>(EMAIL_INPUT_ENTERING_EMAIL)
    .inputTextToMainContentTextInputItem("integration-test@wallet.build") // Fake email
  awaitUntilScreenWithBody<FormBodyModel>(EMAIL_INPUT_ENTERING_EMAIL)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(EMAIL_INPUT_ENTERING_CODE)
    .inputTextToMainContentVerificationCodeInputItem(
      "123456"
    ) // This code always works for Test Accounts
  awaitUntilScreenWithBody<LoadingBodyModel>(EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER)
  awaitUntilScreenWithBody<SuccessBodyModel>(NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL)
}
