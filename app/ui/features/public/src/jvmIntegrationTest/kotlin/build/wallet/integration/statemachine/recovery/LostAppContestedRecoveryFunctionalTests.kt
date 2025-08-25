package build.wallet.integration.statemachine.recovery

import app.cash.turbine.turbineScope
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.*
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.feature.setFlagValue
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.input.VerificationCodeInputFormBodyModel
import build.wallet.statemachine.core.testIn
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.platform.permissions.EnableNotificationsBodyModel
import build.wallet.statemachine.recovery.cloud.CloudWarningBodyModel
import build.wallet.statemachine.recovery.conflict.model.ShowingNoLongerRecoveringBodyModel
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionBodyModel
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.inprogress.RecoverYourAppKeyBodyModel
import build.wallet.statemachine.recovery.lostapp.initiate.RecoveryConflictBodyModel
import build.wallet.statemachine.recovery.sweep.ZeroBalancePromptBodyModel
import build.wallet.statemachine.recovery.verification.ChooseRecoveryNotificationVerificationMethodModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.deleteBackupsFromFakeCloud
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.tags.TestTag.FlakyTest
import build.wallet.ui.model.alert.ButtonAlertModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration.Companion.seconds

class LostAppContestedRecoveryFunctionalTests : FunSpec({

  test("complete lost hardware recovery then lost app recovery") {
    testWithTwoApps(
      isContested = false
    ) { lostHwAppTester, lostAppAppTester, resetHardwareAndClearBackups, _ ->
      lostHwAppTester.initiateAndCompleteLostHardwareRecovery(isConflicted = false)

      resetHardwareAndClearBackups()

      lostAppAppTester.initiateAndCompleteLostAppRecovery(isConflicted = false)
    }
  }

  test("complete lost app recovery then lost hardware recovery")
    .config(tags = setOf(FlakyTest)) {
      testWithTwoApps(
        isContested = false,
        isUsingSocRecFakes = true
      ) { _, lostAppAppTester, resetHardwareAndClearBackups, _ ->
        lostAppAppTester.initiateAndCompleteLostAppRecovery(isConflicted = false)

        resetHardwareAndClearBackups()

        lostAppAppTester.initiateAndCompleteLostHardwareRecovery(isConflicted = false)
      }
    }

  test("conflicted state cleared by completing recovery") {
    testWithTwoApps(
      isContested = true,
      isUsingSocRecFakes = true
    ) { lostHwAppTester, _, resetHardwareAndClearBackups, resetLostHwAppHardware ->

      resetLostHwAppHardware()

      // Initiate and complete recovery
      lostHwAppTester.initiateAndCompleteLostHardwareRecovery(isConflicted = true)

      resetHardwareAndClearBackups()

      lostHwAppTester.initiateAndCompleteLostHardwareRecovery(isConflicted = false)
    }
  }

  listOf(false, true).forEach { isContested ->

    val type =
      if (isContested) {
        "contested"
      } else {
        "uncontested"
      }

    test("initiate lost hw recovery and cancel using hardware: $type") {
      testWithTwoApps(isContested = isContested) { lostHwAppTester, lostAppAppTester, _, resetLostHwAppHardware ->
        resetLostHwAppHardware()
        lostHwAppTester.initiateLostHardwareRecovery(isContested = isContested)
        lostAppAppTester.initiateLostAppRecovery(
          cancelOtherRecovery = true,
          isContested = isContested
        )
      }
    }

    test("initiate lost app recovery and cancel from onboarded app: $type") {
      testWithTwoApps(isContested = isContested) { lostHwAppTester, lostAppAppTester, resetHardwareAndClearBackups, _ ->
        lostAppAppTester.initiateLostAppRecovery(isContested = isContested)
        resetHardwareAndClearBackups()
        lostHwAppTester.initiateLostHardwareRecovery(
          isContested = isContested,
          cancelOtherRecovery = true
        )
      }
    }

    test("initiate lost app recovery and cancel own recovery: $type") {
      testWithTwoApps(isContested = isContested) { _, lostAppAppTester, _, _ ->
        lostAppAppTester.initiateLostAppRecovery(
          isContested = isContested,
          cancelOtherRecovery = false
        ).onStopRecovery.shouldNotBeNull().invoke()
        lostAppAppTester.awaitItem().alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
        lostAppAppTester.awaitUntilBody<ChooseAccountAccessModel>()
      }
    }

    test("initiate lost hardware recovery and cancel own recovery: $type") {
      testWithTwoApps(isContested = isContested) { lostHwAppTester, _, _, resetLostHwAppHardware ->
        resetLostHwAppHardware()
        lostHwAppTester.initiateLostHardwareRecovery(
          isContested = isContested
        ).onStopRecovery.shouldNotBeNull().invoke()
        lostHwAppTester.awaitItem().alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
        lostHwAppTester.awaitUntilBody<SecurityHubBodyModel>()
      }
    }
  }
})

private suspend fun StateMachineTester<Unit, ScreenModel>.initiateLostHardwareRecovery(
  cancelOtherRecovery: Boolean = false,
  isContested: Boolean,
): DelayAndNotifyNewKeyReady {
  awaitUntilBody<MoneyHomeBodyModel>()
    .onSecurityHubTabClick()

  awaitUntilBody<SecurityHubBodyModel>()
    .clickBitkeyDevice()

  awaitUntilBody<DeviceSettingsFormBodyModel>(
    matching = { it.replaceDeviceEnabled && it.replacementPending == null }
  ).onReplaceDevice()
  awaitUntilBody<HardwareReplacementInstructionsModel>()
    .clickPrimaryButton()
  awaitUntilBody<NewDeviceReadyQuestionBodyModel>()
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  if (cancelOtherRecovery) {
    awaitUntilBody<RecoveryConflictBodyModel>(
      LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
    ).onCancelRecovery()
    // Verify to cancel other recovery.
    verifyCommsForLostHardware()
  }
  // Verify (again, possibly) to create new recovery.
  if (isContested) {
    verifyCommsForLostHardware()
  }
  return awaitUntilBody<DelayAndNotifyNewKeyReady>().also {
    it.factorToRecover.shouldBe(PhysicalFactor.Hardware)
  }
}

private suspend fun StateMachineTester<Unit, ScreenModel>.initiateAndCompleteLostHardwareRecovery(
  isConflicted: Boolean,
) {
  initiateLostHardwareRecovery(isContested = isConflicted)
    .primaryButton
    .shouldNotBeNull()
    .onClick()

  // Start onboarding.
  awaitUntilBody<SaveBackupInstructionsBodyModel>()
    .onBackupClick()

  awaitUntilBody<CloudSignInModelFake>()
    .signInSuccess(CloudStoreAccount1Fake)

  awaitUntilBody<ZeroBalancePromptBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
    .onDone()

  awaitUntilBody<SecurityHubBodyModel>().onHomeTabClick()
  awaitUntilBody<MoneyHomeBodyModel>()
}

private suspend fun StateMachineTester<Unit, ScreenModel>.verifyCommsForLostHardware() {
  awaitUntilBody<ChooseRecoveryNotificationVerificationMethodModel>()
    .onEmailClick.shouldNotBeNull().invoke()

  awaitUntilBody<VerificationCodeInputFormBodyModel>(
    LOST_HW_DELAY_NOTIFY_VERIFICATION_ENTRY
  ).onValueChange("123456")
}

private suspend fun StateMachineTester<Unit, ScreenModel>.navigateToLostAppRecovery() {
  awaitUntilBody<ChooseAccountAccessModel>()
    .clickMoreOptionsButton()
  awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
    .onRestoreYourWalletClick()
  awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
    .signInSuccess(CloudStoreAccount1Fake)
  // Cloud sign in missing backup
  awaitUntilBody<CloudWarningBodyModel>(CLOUD_BACKUP_NOT_FOUND)
    .onCannotAccessCloud()
  awaitUntilBody<RecoverYourAppKeyBodyModel>()
    .onStartRecovery()
  awaitUntilBody<EnableNotificationsBodyModel>()
    .clickPrimaryButton()
}

private suspend fun StateMachineTester<Unit, ScreenModel>.cancelThroughLostAppRecovery() {
  navigateToLostAppRecovery()
  // If canceling, perform the following steps.
  awaitUntilBody<RecoveryConflictBodyModel>(
    LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
  ).onCancelRecovery()
  awaitUntilBody<ChooseRecoveryNotificationVerificationMethodModel>()
    .onBack()
}

private suspend fun StateMachineTester<Unit, ScreenModel>.initiateLostAppRecovery(
  cancelOtherRecovery: Boolean = false,
  isContested: Boolean = false,
): DelayAndNotifyNewKeyReady {
  navigateToLostAppRecovery()
  if (cancelOtherRecovery) {
    awaitUntilBody<RecoveryConflictBodyModel>(
      LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
    ).onCancelRecovery()
    // Verify to cancel other recovery.
    verifyCommsForLostApp()
  }
  // Verify (again, possibly) to create new recovery.
  if (isContested) {
    verifyCommsForLostApp()
  }
  return awaitUntilBody<DelayAndNotifyNewKeyReady>().also {
    it.factorToRecover.shouldBe(PhysicalFactor.App)
  }
}

private suspend fun StateMachineTester<Unit, ScreenModel>.initiateAndCompleteLostAppRecovery(
  isConflicted: Boolean,
) {
  initiateLostAppRecovery(isContested = isConflicted)
    .onCompleteRecovery()

  // Start onboarding.
  awaitUntilBody<SaveBackupInstructionsBodyModel>()
    .onBackupClick()

  awaitUntilBody<CloudSignInModelFake>()
    .signInSuccess(CloudStoreAccount1Fake)

  awaitUntilBody<ZeroBalancePromptBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
    .onDone()
}

private suspend fun StateMachineTester<Unit, ScreenModel>.verifyCommsForLostApp() {
  awaitUntilBody<ChooseRecoveryNotificationVerificationMethodModel>()
    .onEmailClick.shouldNotBeNull().invoke()
  awaitUntilBody<VerificationCodeInputFormBodyModel>(
    LOST_APP_DELAY_NOTIFY_VERIFICATION_ENTRY
  ).onValueChange("123456")
}

private suspend fun TestScope.testWithTwoApps(
  isContested: Boolean,
  isUsingSocRecFakes: Boolean = false,
  testContent: suspend CoroutineScope.(
    lostHwAppTester: StateMachineTester<Unit, ScreenModel>,
    lostAppAppTester: StateMachineTester<Unit, ScreenModel>,
    resetHardwareAndClearBackups: suspend () -> Unit,
    resetLostHwAppHardware: suspend () -> Unit,
  ) -> Unit,
) {
  // Setup lost hardware
  val lostHwApp = launchNewApp(isUsingSocRecFakes = isUsingSocRecFakes)
  lostHwApp.encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)
  lostHwApp.onboardFullAccountWithFakeHardware(true, delayNotifyDuration = 2.seconds)
  val fakeHardwareSeed = lostHwApp.fakeNfcCommands.fakeHardwareKeyStore.getSeed()
  lostHwApp.deleteBackupsFromFakeCloud()
  lostHwApp.fakeNfcCommands.wipeDevice()

  // Move the hardware that was lost to a new device
  val lostAppApp = launchNewApp(
    isUsingSocRecFakes = isUsingSocRecFakes,
    hardwareSeed = fakeHardwareSeed
  )
  lostAppApp.encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)

  turbineScope(timeout = 30.seconds) {
    val lostAppAppTester =
      lostAppApp.appUiStateMachine.testIn(
        props = Unit,
        turbineTimeout = 20.seconds,
        scope = this
      )

    val lostHwAppTester =
      lostHwApp.appUiStateMachine.testIn(
        props = Unit,
        turbineTimeout = 20.seconds,
        scope = this
      )

    if (isContested) {
      // Initiate lost hw recovery
      lostHwAppTester.initiateLostHardwareRecovery(isContested = false)

      // Cancel through lost app recovery.
      lostAppAppTester.cancelThroughLostAppRecovery()

      // Wait until sync navigates to the recovery canceled screen and acknowledge.
      lostHwAppTester.awaitUntilBody<ShowingNoLongerRecoveringBodyModel>()
        .onAcknowledge()
    }

    // Set seed if it wasn't set already
    lostHwApp.fakeNfcCommands.fakeHardwareKeyStore.getAuthKeypair()
    val oldSeed = lostHwApp.fakeNfcCommands.fakeHardwareKeyStore.getSeed()

    testContent(
      this,
      lostHwAppTester,
      lostAppAppTester,
      // Reset hardware and clear backups
      {
        lostHwApp.deleteBackupsFromFakeCloud()
        lostHwApp.fakeNfcCommands.wipeDevice()
        lostAppApp.fakeNfcCommands.wipeDevice()
        lostAppApp.fakeNfcCommands.fakeHardwareKeyStore.setSeed(oldSeed)
      },
      // Reset lostHwAppTester's hardware
      {
        lostHwApp.fakeNfcCommands.wipeDevice()
      }
    )

    lostHwAppTester.cancelAndIgnoreRemainingEvents()
    lostAppAppTester.cancelAndIgnoreRemainingEvents()
  }
}
