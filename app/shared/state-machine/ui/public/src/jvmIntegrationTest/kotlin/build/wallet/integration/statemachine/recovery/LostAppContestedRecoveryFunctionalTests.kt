package build.wallet.integration.statemachine.recovery

import app.cash.turbine.turbineScope
import build.wallet.analytics.events.screen.id.ChooseRecoveryNotificationVerificationMethodScreenId.CHOOSE_RECOVERY_NOTIFICATION_VERIFICATION_METHOD
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_READY
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_VERIFICATION_ENTRY
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_READY
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_VERIFICATION_ENTRY
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Button
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.FormMainContentModel.VerificationCodeInput
import build.wallet.statemachine.core.testIn
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.clickTrailingAccessoryButton
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.deleteBackupsFromFakeCloud
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
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

  test("complete lost app recovery then lost hardware recovery") {
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
        )
          .clickTrailingAccessoryButton()
        lostAppAppTester.awaitItem().alertModel.shouldBeTypeOf<ButtonAlertModel>().onPrimaryButtonClick()
        lostAppAppTester.awaitUntilScreenWithBody<ChooseAccountAccessModel>()
      }
    }

    test("initiate lost hardware recovery and cancel own recovery: $type") {
      testWithTwoApps(isContested = isContested) { lostHwAppTester, _, _, resetLostHwAppHardware ->
        resetLostHwAppHardware()
        lostHwAppTester.initiateLostHardwareRecovery(
          isContested = isContested
        ).clickTrailingAccessoryButton()
        lostHwAppTester.awaitItem().alertModel.shouldBeTypeOf<ButtonAlertModel>().onPrimaryButtonClick()
        lostHwAppTester.awaitUntilScreenWithBody<FormBodyModel>(SETTINGS_DEVICE_INFO)
      }
    }
  }
})

private suspend fun StateMachineTester<Unit, ScreenModel>.initiateLostHardwareRecovery(
  cancelOtherRecovery: Boolean = false,
  isContested: Boolean,
): FormBodyModel {
  awaitUntilScreenWithBody<MoneyHomeBodyModel>()
    .trailingToolbarAccessoryModel
    .shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
    .model.onClick.invoke()

  awaitUntilScreenWithBody<SettingsBodyModel>()
    .sectionModels.flatMap { it.rowModels }
    .find { it.title == "Bitkey Device" }
    .shouldNotBeNull()
    .onClick()
  (
    awaitUntilScreenWithBody<FormBodyModel>()
      .mainContentList.find {
        it is Button && it.item.text == "Replace device"
      } as Button
  )
    .item.onClick()
  awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilScreenWithBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  if (cancelOtherRecovery) {
    awaitUntilScreenWithBody<FormBodyModel>(
      LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
    )
      .clickSecondaryButton()
    // Verify to cancel other recovery.
    verifyCommsForLostHardware()
  }
  // Verify (again, possibly) to create new recovery.
  if (isContested) {
    verifyCommsForLostHardware()
  }
  return awaitUntilScreenWithBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
}

private suspend fun StateMachineTester<Unit, ScreenModel>.initiateAndCompleteLostHardwareRecovery(
  isConflicted: Boolean,
) {
  initiateLostHardwareRecovery(isContested = isConflicted)
    .primaryButton
    .shouldNotBeNull()
    .onClick()

  // Start onboarding.
  awaitUntilScreenWithBody<FormBodyModel>()
    .primaryButton
    .shouldNotBeNull()
    .onClick()

  awaitUntilScreenWithBody<CloudSignInModelFake>()
    .signInSuccess(CloudStoreAccount1Fake)

  awaitUntilScreenWithBody<FormBodyModel>(
    LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
  )
    .primaryButton
    .shouldNotBeNull()
    .onClick()
}

private suspend fun StateMachineTester<Unit, ScreenModel>.verifyCommsForLostHardware() {
  // This should be the [ChooseRecoveryNotificationVerificationMethodModel]
  (
    awaitUntilScreenWithBody<FormBodyModel>(CHOOSE_RECOVERY_NOTIFICATION_VERIFICATION_METHOD)
      .mainContentList[0] as ListGroup
  )
    .listGroupModel
    .items
    .find { it.title == "Email" }.shouldNotBeNull().onClick.shouldNotBeNull()()
  awaitUntilScreenWithBody<FormBodyModel>(
    LOST_HW_DELAY_NOTIFY_VERIFICATION_ENTRY
  ).let {
    (it.mainContentList[0] as VerificationCodeInput).fieldModel.onValueChange(
      "123456",
      IntRange.EMPTY
    )
  }
}

private suspend fun StateMachineTester<Unit, ScreenModel>.navigateToLostAppRecovery() {
  awaitUntilScreenWithBody<ChooseAccountAccessModel>()
    .clickMoreOptionsButton()
  awaitUntilScreenWithBody<FormBodyModel>()
    .restoreButton.onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
    .signInSuccess(CloudStoreAccount1Fake)
  // Cloud sign in missing backup
  awaitUntilScreenWithBody<FormBodyModel>(CLOUD_BACKUP_NOT_FOUND)
    .restoreButton.onClick.shouldNotBeNull().invoke()
  awaitUntilScreenWithBody<FormBodyModel>(
    LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
  )
    .clickPrimaryButton()
  awaitUntilScreenWithBody<FormBodyModel>(ENABLE_PUSH_NOTIFICATIONS)
    .clickPrimaryButton()
}

private suspend fun StateMachineTester<Unit, ScreenModel>.cancelThroughLostAppRecovery() {
  navigateToLostAppRecovery()
  // If canceling, perform the following steps.
  awaitUntilScreenWithBody<FormBodyModel>(
    LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
  )
    .clickSecondaryButton()
  awaitUntilScreenWithBody<FormBodyModel>().onBack.shouldNotBeNull()()
}

private suspend fun StateMachineTester<Unit, ScreenModel>.initiateLostAppRecovery(
  cancelOtherRecovery: Boolean = false,
  isContested: Boolean = false,
): FormBodyModel {
  navigateToLostAppRecovery()
  if (cancelOtherRecovery) {
    awaitUntilScreenWithBody<FormBodyModel>(
      LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
    )
      .clickSecondaryButton()
    // Verify to cancel other recovery.
    verifyCommsForLostApp()
  }
  // Verify (again, possibly) to create new recovery.
  if (isContested) {
    verifyCommsForLostApp()
  }
  return awaitUntilScreenWithBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_READY)
}

private suspend fun StateMachineTester<Unit, ScreenModel>.initiateAndCompleteLostAppRecovery(
  isConflicted: Boolean,
) {
  initiateLostAppRecovery(isContested = isConflicted)
    .primaryButton
    .shouldNotBeNull()
    .onClick()

  // Start onboarding.
  awaitUntilScreenWithBody<FormBodyModel>()
    .primaryButton
    .shouldNotBeNull()
    .onClick()

  awaitUntilScreenWithBody<CloudSignInModelFake>()
    .signInSuccess(CloudStoreAccount1Fake)

  awaitUntilScreenWithBody<FormBodyModel>(
    LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE
  )
    .primaryButton
    .shouldNotBeNull()
    .onClick()
}

private suspend fun StateMachineTester<Unit, ScreenModel>.verifyCommsForLostApp() {
  (
    awaitUntilScreenWithBody<FormBodyModel>(CHOOSE_RECOVERY_NOTIFICATION_VERIFICATION_METHOD)
      .mainContentList[0] as ListGroup
  )
    .listGroupModel
    .items
    .find { it.title == "Email" }.shouldNotBeNull().onClick.shouldNotBeNull()()
  awaitUntilScreenWithBody<FormBodyModel>(
    LOST_APP_DELAY_NOTIFY_VERIFICATION_ENTRY
  ).let {
    (it.mainContentList[0] as VerificationCodeInput).fieldModel.onValueChange(
      "123456",
      IntRange.EMPTY
    )
  }
}

private suspend fun testWithTwoApps(
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
  lostHwApp.onboardFullAccountWithFakeHardware(true, delayNotifyDuration = 2.seconds)
  val fakeHardwareSeed = lostHwApp.fakeNfcCommands.fakeHardwareKeyStore.getSeed()
  lostHwApp.deleteBackupsFromFakeCloud()
  lostHwApp.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()

  // Move the hardware that was lost to a new device
  val lostAppApp = launchNewApp(
    isUsingSocRecFakes = isUsingSocRecFakes,
    hardwareSeed = fakeHardwareSeed
  )

  turbineScope(timeout = 30.seconds) {
    val lostAppAppTester =
      lostAppApp.app.appUiStateMachine.testIn(
        props = Unit,
        turbineTimeout = 20.seconds,
        scope = this
      )

    val lostHwAppTester =
      lostHwApp.app.appUiStateMachine.testIn(
        props = Unit,
        turbineTimeout = 20.seconds,
        scope = this
      )

    if (isContested) {
      // Initiate lost hw recovery and back out to Settings screen. Backing out is a hack
      // to allow waiting for a new FormScreenModel as a recovery canceled screen.
      lostHwAppTester.initiateLostHardwareRecovery(isContested = false)
        .onBack.shouldNotBeNull()()
      lostHwAppTester.awaitUntilScreenWithBody<FormBodyModel>()
        .onBack.shouldNotBeNull()()

      // Cancel through lost app recovery.
      lostAppAppTester.cancelThroughLostAppRecovery()

      // Wait until sync navigates to the recovery canceled screen and acknowledge.
      lostHwAppTester.awaitUntilScreenWithBody<FormBodyModel>().primaryButton.shouldNotBeNull()
        .onClick()
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
        lostHwApp.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()
        lostAppApp.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()
        lostAppApp.fakeNfcCommands.fakeHardwareKeyStore.setSeed(oldSeed)
      },
      // Reset lostHwAppTester's hardware
      {
        lostHwApp.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()
      }
    )

    lostHwAppTester.cancelAndIgnoreRemainingEvents()
    lostAppAppTester.cancelAndIgnoreRemainingEvents()
  }
}
