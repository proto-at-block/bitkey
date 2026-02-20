package build.wallet.integration.statemachine.recovery

import app.cash.turbine.turbineScope
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.*
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
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
import build.wallet.statemachine.recovery.conflict.model.ShowingSomeoneElseIsRecoveringBodyModel
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

  // Critical test: Device A (active account) sees SomeoneElseIsRecovering when Device B initiates recovery
  //
  // This test verifies the LEGITIMATE SomeoneElseIsRecovering case:
  // 1. Device A onboards with HW X (has active account)
  // 2. Device B (with same HW X) initiates Lost App recovery
  // 3. Device A should see "Recovery Conflict" warning (ShowingSomeoneElseIsRecoveringBodyModel)
  //
  // This test would FAIL if the race condition fix is too aggressive and prevents
  // server recovery from being set on Device A (which has an active account but no local recovery).
  test("active account device sees SomeoneElseIsRecovering when another device initiates Lost App recovery") {
    // Device A: onboard and stay at Money Home
    val deviceA = launchNewApp(executeWorkers = true)
    deviceA.onboardFullAccountWithFakeHardware(true, delayNotifyDuration = 2.seconds)

    // Device B: same hardware, no account - will initiate recovery
    val deviceAHardwareSeed = deviceA.fakeNfcCommands.fakeHardwareKeyStore.getSeed()
    val deviceB = launchNewApp(
      hardwareSeed = deviceAHardwareSeed,
      executeWorkers = true
    )

    turbineScope(timeout = 60.seconds) {
      val deviceATester = deviceA.appUiStateMachine.testIn(
        props = Unit,
        turbineTimeout = 30.seconds,
        scope = this
      )

      val deviceBTester = deviceB.appUiStateMachine.testIn(
        props = Unit,
        turbineTimeout = 30.seconds,
        scope = this
      )

      // Device A should be at Money Home
      deviceATester.awaitUntilBody<MoneyHomeBodyModel>()

      // Device B initiates Lost App recovery
      // This will notify the server, creating a server recovery
      deviceBTester.navigateToLostAppRecovery()

      // Device B reaches DelayAndNotifyNewKeyReady (recovery initiated on server)
      deviceBTester.awaitUntilBody<DelayAndNotifyNewKeyReady>().also {
        it.factorToRecover.shouldBe(PhysicalFactor.App)
      }

      // Device A should now see ShowingSomeoneElseIsRecoveringBodyModel
      // because someone else (Device B) is trying to recover the account.
      // The sync worker on Device A should detect the server recovery and show the warning.
      //
      // This assertion would FAIL if the race condition fix is too aggressive
      // and doesn't set server recovery when there's an active account.
      deviceATester.awaitUntilBody<ShowingSomeoneElseIsRecoveringBodyModel>(
        matching = { it.cancelingRecoveryLostFactor == PhysicalFactor.App }
      )

      deviceATester.cancelAndIgnoreRemainingEvents()
      deviceBTester.cancelAndIgnoreRemainingEvents()
    }
  }

  // Race condition test: This test verifies that when a user initiates lost app recovery,
  // they don't incorrectly see "SomeoneElseIsRecovering" due to a race condition between
  // the server sync and local recovery initiation.
  //
  // The race condition that was fixed:
  // 1. User initiates recovery (server is notified)
  // 2. Sync worker fetches server recovery status
  // 3. Server recovery is saved to DB BEFORE local recovery is initiated
  // 4. User incorrectly sees "SomeoneElseIsRecovering"
  //
  // The fix ensures that server recovery is NOT set in the DAO until local recovery is present.
  // This test verifies the fix works by ensuring the recovery flow completes without
  // showing "SomeoneElseIsRecovering".
  test("lost app recovery does not show SomeoneElseIsRecovering - race condition prevention") {
    testWithTwoApps(
      isContested = false
    ) { _, lostAppAppTester, _, _ ->
      // Initiate lost app recovery - this should NOT show SomeoneElseIsRecovering
      // even though the server will be notified and sync workers are running.
      // Without the race condition fix, there's a chance that:
      // 1. Server returns recovery status after notification
      // 2. Sync worker saves server recovery before local recovery is saved
      // 3. App incorrectly shows "SomeoneElseIsRecovering"
      //
      // The fix in RecoveryStatusServiceImpl prevents this by:
      // 1. Checking if local recovery is present before setting server recovery
      // 2. Triggering an immediate sync when local recovery is initiated

      // This call would fail if SomeoneElseIsRecovering was shown instead of
      // the expected DelayAndNotifyNewKeyReady screen
      lostAppAppTester.initiateLostAppRecovery(isContested = false)
        .factorToRecover.shouldBe(PhysicalFactor.App)
    }
  }

  // Another race condition test: Complete the full lost app recovery flow
  // This tests that the entire flow works correctly when background workers are running.
  test("complete lost app recovery - full flow race condition prevention") {
    testWithTwoApps(
      isContested = false,
      isUsingSocRecFakes = true
    ) { _, lostAppAppTester, _, _ ->
      // This complete flow would fail at any point if SomeoneElseIsRecovering was shown
      // The test passes through multiple sync cycles as the recovery progresses,
      // verifying the fix works throughout the entire recovery flow.
      lostAppAppTester.initiateAndCompleteLostAppRecovery(isConflicted = false)
    }
  }

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
      testWithTwoApps(isContested = isContested, executeWorkers = false) { lostHwAppTester, lostAppAppTester, resetHardwareAndClearBackups, _ ->
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
  executeWorkers: Boolean = true,
  testContent: suspend CoroutineScope.(
    lostHwAppTester: StateMachineTester<Unit, ScreenModel>,
    lostAppAppTester: StateMachineTester<Unit, ScreenModel>,
    resetHardwareAndClearBackups: suspend () -> Unit,
    resetLostHwAppHardware: suspend () -> Unit,
  ) -> Unit,
) {
  // Setup lost hardware
  val lostHwApp = launchNewApp(
    isUsingSocRecFakes = isUsingSocRecFakes,
    executeWorkers = executeWorkers
  )
  lostHwApp.onboardFullAccountWithFakeHardware(true, delayNotifyDuration = 2.seconds)
  val fakeHardwareSeed = lostHwApp.fakeNfcCommands.fakeHardwareKeyStore.getSeed()
  lostHwApp.deleteBackupsFromFakeCloud(FullAccountIdMock)
  lostHwApp.fakeNfcCommands.wipeDevice()

  // Move the hardware that was lost to a new device
  val lostAppApp = launchNewApp(
    isUsingSocRecFakes = isUsingSocRecFakes,
    hardwareSeed = fakeHardwareSeed,
    executeWorkers = executeWorkers
  )

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
        lostHwApp.deleteBackupsFromFakeCloud(FullAccountIdMock)
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
