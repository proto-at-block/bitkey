package build.wallet.integration.statemachine.create

import app.cash.turbine.ReceiveTurbine
import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.feature.setFlagValue
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStep.*
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryNotificationsSetupFormBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.input.EmailInputScreenModel
import build.wallet.statemachine.core.input.PhoneNumberInputBodyModel
import build.wallet.statemachine.core.input.VerificationCodeInputFormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.notifications.NotificationPreferenceFormBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickSetUpNewWalletButton
import build.wallet.testing.AppTester
import build.wallet.testing.ext.HardwareCoverageMode
import build.wallet.testing.ext.assertActiveHardwareType
import build.wallet.testing.ext.testForHardwareHappyPaths
import build.wallet.testing.ext.verifyPostOnboardingState
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class CreateAndOnboardFullAccountFunctionalTests : FunSpec({

  suspend fun AppTester.prepareApp(coverageMode: HardwareCoverageMode): AppTester {
    return apply {
      // Set push notifications to authorized to enable us to successfully advance through
      // the notifications step in onboarding.
      pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.Authorized
      )
      accountConfigService.setHardwareType(coverageMode.hardwareType).getOrThrow()
      w3OnboardingFeatureFlag.setFlagValue(coverageMode == HardwareCoverageMode.W3Private)
    }
  }

  testForHardwareHappyPaths("happy path through create and then onboard and activate keybox") { app, coverageMode ->
    app.prepareApp(coverageMode)
    app.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 20.seconds
    ) {
      val onboardingSteps = buildList {
        add(CloudBackup)
        add(NotificationPreferences)
        if (coverageMode == HardwareCoverageMode.W3Private) {
          add(BuildHardwareDescriptor)
        }
      }
      advanceThroughCreateKeyboxScreens(coverageMode)
      advanceThroughOnboardKeyboxScreens(onboardingSteps, hardwareType = coverageMode.hardwareType)
      awaitUntilBody<LoadingSuccessBodyModel>(LOADING_SAVING_KEYBOX) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
    app.verifyPostOnboardingState()
  }

  testForHardwareHappyPaths("close and reopen app to cloud backup onboard step") { app, coverageMode ->
    app.prepareApp(coverageMode)
    app.testCloseAndReopenAppToOnboardingScreen<FormBodyModel>(
      coverageMode = coverageMode,
      stepsToAdvance = emptyList(),
      screenIdExpectation = SAVE_CLOUD_BACKUP_INSTRUCTIONS
    )
  }

  testForHardwareHappyPaths("close and reopen app to notification pref onboard step") { app, coverageMode ->
    app.prepareApp(coverageMode)
    app.testCloseAndReopenAppToOnboardingScreen<EmailInputScreenModel>(
      coverageMode = coverageMode,
      stepsToAdvance = listOf(CloudBackup),
      screenIdExpectation = EMAIL_INPUT_ENTERING_EMAIL
    )
  }
})

private suspend inline fun <reified T : BodyModel> AppTester.testCloseAndReopenAppToOnboardingScreen(
  coverageMode: HardwareCoverageMode,
  stepsToAdvance: List<OnboardingKeyboxStep>,
  screenIdExpectation: EventTrackerScreenId,
) {
  appUiStateMachine.test(Unit) {
    advanceThroughCreateKeyboxScreens(coverageMode)
    advanceThroughOnboardKeyboxScreens(stepsToAdvance, hardwareType = coverageMode.hardwareType)
    awaitUntilBody<T>(screenIdExpectation)
    cancelAndIgnoreRemainingEvents()
  }

  val newApp = relaunchApp()
  newApp.appUiStateMachine.test(Unit) {
    awaitUntilBody<T>(screenIdExpectation)
    cancelAndIgnoreRemainingEvents()
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCreateKeyboxScreens(
  coverageMode: HardwareCoverageMode = HardwareCoverageMode.W1Baseline,
) {
  awaitUntilBody<ChooseAccountAccessModel>()
    .clickSetUpNewWalletButton()

  when (coverageMode) {
    HardwareCoverageMode.W1Baseline -> {
      awaitUntilBody<PairNewHardwareBodyModel>(
        HW_ACTIVATION_INSTRUCTIONS,
        matching = { !it.primaryButton.isLoading }
      ) {
        clickPrimaryButton()
      }
      awaitUntilBody<PairNewHardwareBodyModel>(
        HW_PAIR_INSTRUCTIONS,
        matching = { !it.primaryButton.isLoading }
      ) {
        clickPrimaryButton()
      }
      awaitUntilBody<PairNewHardwareBodyModel>(
        HW_SAVE_FINGERPRINT_INSTRUCTIONS,
        matching = { !it.primaryButton.isLoading }
      ) {
        clickPrimaryButton()
      }
    }
    HardwareCoverageMode.W3Private -> {
      awaitUntilBody<PairNewHardwareBodyModel>(
        HW_ACTIVATION_INSTRUCTIONS_V2,
        matching = { !it.primaryButton.isLoading }
      ) {
        clickPrimaryButton()
      }
      awaitUntilBody<CompleteTwoTapBodyModel>(HW_COMPLETE_TWO_TAP) {
        clickPrimaryButton()
      }
    }
  }
  awaitUntilBody<LoadingSuccessBodyModel>(NEW_ACCOUNT_SERVER_KEYS_LOADING) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughOnboardKeyboxScreens(
  stepsToAdvance: List<OnboardingKeyboxStep>,
  isCloudBackupSkipSignIn: Boolean = false,
  hardwareType: HardwareType = HardwareType.W1,
) {
  stepsToAdvance.forEach { step ->
    when (step) {
      CloudBackup -> {
        if (isCloudBackupSkipSignIn) {
          awaitUntilBody<LoadingSuccessBodyModel>(CLOUD_SIGN_IN_LOADING) {
            state.shouldBe(LoadingSuccessBodyModel.State.Loading)
          }
        } else {
          awaitUntilBody<SaveBackupInstructionsBodyModel>()
            .onBackupClick()
          awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
            .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
        }
      }

      NotificationPreferences -> advanceThroughOnboardingNotificationSetupScreens(hardwareType)
      DescriptorBackup -> {
        // no-op, auto-progresses to cloud backup
      }

      BuildHardwareDescriptor -> {
        awaitUntilBody<LoadingSuccessBodyModel>(LOADING_ONBOARDING_STEP) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }

        awaitUntilBody<PairNewHardwareBodyModel>(BUILD_HARDWARE_DESCRIPTOR_INTRO)
          .clickPrimaryButton()
      }
    }
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughOnboardingNotificationSetupScreens(
  hardwareType: HardwareType = HardwareType.W1,
) {
  // The flow is always sequential: Email → SMS (optional) → Push setup (optional) → Transactions
  when (val initialBody = awaitNextOnboardingNotificationBody()) {
    is EmailInputScreenModel -> {
      advanceThroughEmailScreensEnterAndVerify(initialBody, hardwareType = hardwareType)
    }

    is PhoneNumberInputBodyModel -> {
      advanceThroughSmsScreensEnterAndVerify(initialBody, hardwareType = hardwareType)
    }

    is RecoveryNotificationsSetupFormBodyModel -> {
      // Push setup: skip to advance to transactions
      initialBody.onSkip()
    }

    is NotificationPreferenceFormBodyModel -> {
      completeNotificationPreferences(initialBody)
      return
    }
  }

  // Continue advancing through remaining steps
  var done = false
  while (!done) {
    when (val body = awaitNextOnboardingNotificationBody()) {
      is PhoneNumberInputBodyModel -> {
        advanceThroughSmsScreensEnterAndVerify(body, hardwareType = hardwareType)
      }

      is RecoveryNotificationsSetupFormBodyModel -> {
        // Push setup: skip to advance to transactions
        body.onSkip()
      }

      is NotificationPreferenceFormBodyModel -> {
        completeNotificationPreferences(body)
        done = true
      }

      is EmailInputScreenModel -> {
        error("Unexpectedly returned to email entry after completing email verification.")
      }
    }
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughEmailScreensEnterAndVerify(
  initialScreen: EmailInputScreenModel? = null,
  hardwareType: HardwareType = HardwareType.W1,
) {
  (initialScreen ?: awaitUntilBody<EmailInputScreenModel>())
    .onValueChange("integration-test@wallet.build") // Fake email
  awaitUntilBody<EmailInputScreenModel>(
    matching = { it.primaryButton.isEnabled }
  ) {
    clickPrimaryButton()
  }
  awaitUntilBody<VerificationCodeInputFormBodyModel>()
    .onValueChange("123456") // This code always works for Test Accounts
  awaitUntilBody<LoadingSuccessBodyModel>(EMAIL_INPUT_SENDING_CODE_TO_SERVER)
  advanceThroughNotificationActivation(
    successScreenId = NOTIFICATIONS_HW_APPROVAL_SUCCESS_EMAIL,
    hardwareType = hardwareType
  )
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughSmsScreensEnterAndVerify(
  initialScreen: PhoneNumberInputBodyModel? = null,
  hardwareType: HardwareType = HardwareType.W1,
) {
  val phoneNumber = "+12015550123"
  (initialScreen ?: awaitUntilBody<PhoneNumberInputBodyModel>())
    .onTextFieldValueChange(phoneNumber, phoneNumber.length..phoneNumber.length)
  awaitUntilBody<PhoneNumberInputBodyModel>(
    matching = { it.primaryButton.isEnabled }
  ) {
    clickPrimaryButton()
  }
  awaitUntilBody<VerificationCodeInputFormBodyModel>()
    .onValueChange("123456")
  awaitUntilBody<LoadingSuccessBodyModel>(SMS_INPUT_SENDING_CODE_TO_SERVER)
  advanceThroughNotificationActivation(
    successScreenId = NOTIFICATIONS_HW_APPROVAL_SUCCESS_SMS,
    hardwareType = hardwareType
  )
}

private suspend fun ReceiveTurbine<ScreenModel>.completeNotificationPreferences(
  initialScreen: NotificationPreferenceFormBodyModel? = null,
) {
  val screen = initialScreen ?: awaitUntilBody<NotificationPreferenceFormBodyModel>()
  screen.continueOnClick()
}

private suspend fun ReceiveTurbine<ScreenModel>.awaitNextOnboardingNotificationBody(): BodyModel {
  return awaitUntilScreenWithBody<BodyModel>(
    matchingBody = { body ->
      body is EmailInputScreenModel ||
        body is PhoneNumberInputBodyModel ||
        body is RecoveryNotificationsSetupFormBodyModel ||
        body is NotificationPreferenceFormBodyModel
    }
  ).body
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughNotificationActivation(
  successScreenId: EventTrackerScreenId,
  hardwareType: HardwareType = HardwareType.W1,
) {
  when (hardwareType) {
    HardwareType.W1 -> {
      // W1 fake hardware auto-completes the NFC approval; the success screen arrives directly.
      awaitUntilBody<LoadingSuccessBodyModel>(successScreenId)
    }
    HardwareType.W3 -> {
      // W3 shows approval instructions, then an NFC session with the emulated prompt in a
      // bottom sheet.  Approve the prompt, confirm the second tap, then await success.
      awaitUntilBody<FormBodyModel>(NOTIFICATIONS_HW_APPROVAL) {
        primaryButton?.onClick?.invoke()
      }
      awaitUntilScreenWithBody<BodyModel>(
        matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
      ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
      awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
      awaitUntilBody<LoadingSuccessBodyModel>(successScreenId)
    }
  }
}
