package build.wallet.integration.statemachine.create

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_ACCOUNT_SERVER_KEYS_LOADING
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.EMAIL_INPUT_ENTERING_CODE
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.EMAIL_INPUT_ENTERING_EMAIL
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.EMAIL_INPUT_SENDING_CODE_TO_SERVER
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.NOTIFICATION_PREFERENCES_SELECTION
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.NOTIFICATION_PREFERENCES_SETUP
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickMainContentListItemAtIndex
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.inputTextToMainContentTextInputItem
import build.wallet.statemachine.ui.inputTextToMainContentVerificationCodeInputItem
import build.wallet.statemachine.ui.robots.clickSetUpNewWalletButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.tags.TestTag.FlakyTest
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.list.ListItemAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class CreateAndOnboardFullAccountFunctionalTests : FunSpec() {
  lateinit var appTester: AppTester

  init {
    beforeEach {
      appTester = launchNewApp()

      // Set push notifications to authorized to enable us to successfully advance through
      // the notifications step in onboarding.
      appTester.app.appComponent.pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.Authorized
      )
    }

    test("happy path through create and then onboard and activate keybox")
      .config(tags = setOf(FlakyTest)) {

        appTester.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
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
    appTester.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(stepsToAdvance)
      awaitUntilScreenWithBody<T>(screenIdExpectation)
      cancelAndIgnoreRemainingEvents()
    }

    val newAppTester = appTester.relaunchApp()
    newAppTester.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
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
          awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
            .clickPrimaryButton()
          awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
            .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
        }
      }

      NotificationPreferences -> {
        awaitUntilScreenWithBody<FormBodyModel>(NOTIFICATION_PREFERENCES_SETUP)
          .clickMainContentListItemAtIndex(0) // Tap email row item
        advanceThroughEmailScreensEnterAndVerify()

        // Check that they email touchpoint has propagated back to the state machine
        // It propagates through the [notificationTouchpointDao], but if it hasn't been
        // received before returning to this screen, will cause a recomposition and the
        // continue button won't progress forward.
        awaitUntilScreenWithBody<FormBodyModel>(
          id = NOTIFICATION_PREFERENCES_SETUP,
          expectedBodyContentMatch = {
            it.mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup>()
              .listGroupModel.items[0].trailingAccessory.shouldNotBeNull()
              .shouldBeTypeOf<ListItemAccessory.IconAccessory>()
              .model.iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon == Icon.SmallIconCheckFilled
          }
        )
          .clickPrimaryButton()

        // Accept the TOS
        awaitUntilScreenWithBody<FormBodyModel>(NOTIFICATION_PREFERENCES_SELECTION)
          .mainContentList[4].shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel.items[0].trailingAccessory.shouldNotBeNull()
          .shouldBeTypeOf<ListItemAccessory.IconAccessory>()
          .onClick.shouldNotBeNull().invoke()

        awaitUntilScreenWithBody<FormBodyModel>(NOTIFICATION_PREFERENCES_SELECTION)
          .clickPrimaryButton()
      }
    }
  }
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughEmailScreensEnterAndVerify() {
  awaitUntilScreenWithBody<FormBodyModel>(EMAIL_INPUT_ENTERING_EMAIL)
    .inputTextToMainContentTextInputItem("integration-test@wallet.build") // Fake email
  awaitUntilScreenWithBody<FormBodyModel>(
    EMAIL_INPUT_ENTERING_EMAIL,
    expectedBodyContentMatch = {
      it.primaryButton?.isEnabled == true
    }
  ) {
    clickPrimaryButton()
  }
  awaitUntilScreenWithBody<FormBodyModel>(EMAIL_INPUT_ENTERING_CODE)
    .inputTextToMainContentVerificationCodeInputItem(
      "123456"
    ) // This code always works for Test Accounts
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(EMAIL_INPUT_SENDING_CODE_TO_SERVER)
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(EMAIL_INPUT_SENDING_ACTIVATION_TO_SERVER)
  awaitUntilScreenWithBody<LoadingSuccessBodyModel>(NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL)
}
