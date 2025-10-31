package build.wallet.integration.statemachine.create

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_ACCOUNT_SERVER_KEYS_LOADING
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.*
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.f8e.recovery.PrivateMultisigRemoteKeyset
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.DescriptorBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.account.create.full.onboard.notifications.ConfirmSkipRecoveryMethodsSheetModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormBodyModel
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.Completed
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
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickSetUpNewWalletButton
import build.wallet.testing.AppTester
import build.wallet.testing.ext.testForLegacyAndPrivateWallet
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class CreateAndOnboardFullAccountFunctionalTests : FunSpec({

  fun AppTester.prepareApp(): AppTester {
    return apply {
      // Set push notifications to authorized to enable us to successfully advance through
      // the notifications step in onboarding.
      pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
        PermissionStatus.Authorized
      )
    }
  }

  testForLegacyAndPrivateWallet("happy path through create and then onboard and activate keybox") { app ->
    app.prepareApp()
    app.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 20.seconds
    ) {
      advanceThroughCreateKeyboxScreens()
      advanceThroughOnboardKeyboxScreens(
        listOf(CloudBackup, NotificationPreferences)
      )
      awaitUntilBody<LoadingSuccessBodyModel>(LOADING_SAVING_KEYBOX) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    val account = app.accountService.activeAccount().first().shouldBeTypeOf<FullAccount>()

    var observedActiveKeybox: Keybox? = null

    // Assertions on keybox state
    app.keyboxDao.activeKeybox().first().shouldBeOk { keybox ->
      val activeKeybox = keybox.shouldNotBeNull()
      activeKeybox.fullAccountId.shouldBeEqual(account.accountId)
      activeKeybox.canUseKeyboxKeysets.shouldBe(true)
      observedActiveKeybox = activeKeybox
    }
    val activeKeyboxValue = observedActiveKeybox.shouldNotBeNull()

    // Assert onboarding stuff is cleared.
    app.keyboxDao.onboardingKeybox().first().shouldBeOk { onboardingKeybox ->
      onboardingKeybox.shouldBeNull()
    }
    app.onboardingAppKeyKeystore
      .getAppKeyBundle(
        localId = activeKeyboxValue.activeAppKeyBundle.localId,
        network = account.config.bitcoinNetworkType
      ).shouldBeNull()
    app.onboardingKeyboxHwAuthPublicKeyDao.get().shouldBeOk { hardwareKeys ->
      hardwareKeys.shouldBeNull()
    }
    app.onboardingKeyboxSealedSsekDao.get().shouldBeOk { sealedSsek ->
      sealedSsek.shouldBeNull()
    }

    // Assertions on cloud backup state
    app.cloudBackupRepository
      .readActiveBackup(CloudStoreAccountFake.CloudStoreAccount1Fake)
      .getOrThrow()
      .shouldNotBeNull()

    // Assertion on EEK state
    app.cloudFileStore
      .exists(
        account = CloudStoreAccountFake.CloudStoreAccount1Fake,
        fileName = "Emergency Exit Kit.pdf"
      ).result.shouldBeOk { exists ->
        exists.shouldBe(true)
      }

    val keysets = app.listKeysetsF8eClient
      .listKeysets(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      ).getOrThrow().keysets

    // Basic keyset assertions
    keysets.shouldNotBeEmpty()
    keysets.size.shouldBeEqual(1)

    // Check of resulting descriptor backup
    val activeKeyset = keysets.first()
    if (activeKeyset is PrivateMultisigRemoteKeyset) {
      app.descriptorBackupService
        .checkBackupForPrivateKeyset(keysetId = activeKeyset.keysetId)
        .shouldBeOk()
    }
  }

  testForLegacyAndPrivateWallet("close and reopen app to cloud backup onboard step") { app ->
    app.prepareApp()
    app.testCloseAndReopenAppToOnboardingScreen<FormBodyModel>(
      stepsToAdvance = emptyList(),
      screenIdExpectation = SAVE_CLOUD_BACKUP_INSTRUCTIONS
    )
  }

  testForLegacyAndPrivateWallet("close and reopen app to notification pref onboard step") { app ->
    app.prepareApp()
    app.testCloseAndReopenAppToOnboardingScreen<FormBodyModel>(
      stepsToAdvance = listOf(CloudBackup),
      screenIdExpectation = NOTIFICATION_PREFERENCES_SETUP
    )
  }
})

private suspend inline fun <reified T : BodyModel> AppTester.testCloseAndReopenAppToOnboardingScreen(
  stepsToAdvance: List<OnboardingKeyboxStep>,
  screenIdExpectation: EventTrackerScreenId,
) {
  appUiStateMachine.test(Unit) {
    advanceThroughCreateKeyboxScreens()
    advanceThroughOnboardKeyboxScreens(stepsToAdvance)
    awaitUntilBody<T>(screenIdExpectation)
    cancelAndIgnoreRemainingEvents()
  }

  val newApp = relaunchApp()
  newApp.appUiStateMachine.test(Unit) {
    awaitUntilBody<T>(screenIdExpectation)
    cancelAndIgnoreRemainingEvents()
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCreateKeyboxScreens() {
  awaitUntilBody<ChooseAccountAccessModel>()
    .clickSetUpNewWalletButton()
  awaitUntilBody<PairNewHardwareBodyModel>(
    HW_ACTIVATION_INSTRUCTIONS,
    matching = {
      // Wait until loading state is cleared from the primary button
      !it.primaryButton.isLoading
    }
  ).clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<LoadingSuccessBodyModel>(NEW_ACCOUNT_SERVER_KEYS_LOADING) {
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

      NotificationPreferences -> advanceThroughOnboardingNotificationSetupScreens()
      DescriptorBackup -> {
        // no-op, auto-progresses to cloud backup
      }
    }
  }
}

internal suspend fun ReceiveTurbine<ScreenModel>.advanceThroughOnboardingNotificationSetupScreens() {
  val recoverySetupScreen = awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
  val isUsSmsFeatureFlagEnabled = recoverySetupScreen.smsItem != null

  recoverySetupScreen.emailItem.onClick.shouldNotBeNull().invoke()
  advanceThroughEmailScreensEnterAndVerify()

  // Check that the email touchpoint has propagated back to the state machine
  // It propagates through the [notificationTouchpointDao], but if it hasn't been
  // received before returning to this screen, will cause a recomposition and the
  // continue button won't progress forward.
  awaitUntilBody<RecoveryChannelsSetupFormBodyModel>(
    matching = { it.emailItem.state == Completed }
  ) {
    continueOnClick()
  }

  if (isUsSmsFeatureFlagEnabled) {
    awaitItem()
      .bottomSheetModel
      .shouldNotBeNull()
      .body
      .shouldBeTypeOf<ConfirmSkipRecoveryMethodsSheetModel>()
      .onContinue()
  }

  // Accept the TOS
  awaitUntilBody<NotificationPreferenceFormBodyModel>()
    .tosInfo.shouldNotBeNull().onTermsAgreeToggle(true)

  awaitUntilBody<NotificationPreferenceFormBodyModel>()
    .continueOnClick()
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughEmailScreensEnterAndVerify() {
  awaitUntilBody<EmailInputScreenModel>()
    .onValueChange("integration-test@wallet.build") // Fake email
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
