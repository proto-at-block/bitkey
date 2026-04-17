package build.wallet.integration.statemachine.recovery

import app.cash.turbine.ReceiveTurbine
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.*
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionBodyModel
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.inprogress.waiting.HardwareDelayNotifyInProgressScreenModel
import build.wallet.statemachine.recovery.sweep.ZeroBalancePromptBodyModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.integration.statemachine.recovery.socrec.awaitW3ConfirmableNfcSession
import build.wallet.testing.ext.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LostHardwareRecoveryForceExitFunctionalTests : FunSpec({
  suspend fun AppTester.prepareApp(delayNotifyDuration: Duration = 1.seconds) {
    onboardFullAccountWithFakeHardware(delayNotifyDuration = delayNotifyDuration)
    fakeNfcCommands.wipeDevice()
  }

  suspend fun TestScope.prepareW3App(transition: RecoveryHardwareTransition): AppTester {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware(hardwareType = transition.sourceHardwareType)
    app.fakeW3NfcCommands.wipeDevice()
    app.defaultAccountConfigService.setHardwareType(transition.replacementHardwareType)
    return app
  }

  testForLegacyAndPrivateWallet(
    "recovery lost hardware - force exiting before cloud backup takes you back to icloud backup"
  ) { initialApp ->
    var app = initialApp
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
    }

    // Force exit app while on cloud backup step
    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Resume on cloud backup step and complete recovery
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("recovery lost hardware - force exiting in the middle of initiation") { initialApp ->
    var app = initialApp
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Initiate recovery
      awaitUntilBody<MoneyHomeBodyModel>()
        .onSecurityHubTabClick()
      awaitUntilBody<SecurityHubBodyModel>()
        .clickBitkeyDevice()
      awaitUntilBody<DeviceSettingsFormBodyModel>()
        .onReplaceDevice()
      awaitUntilBody<HardwareReplacementInstructionsModel>()
        .onContinue()
      awaitUntilBody<NewDeviceReadyQuestionBodyModel>()
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS_V2)
        .clickPrimaryButton()
      // V2: NFC completes instantly for fakes → fingerprint enrollment screen appears
      awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)

      cancelAndIgnoreRemainingEvents()
    }

    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // No recovery started, start over
      app.awaitNoActiveRecovery()
      startRecoveryAndAdvanceToDelayNotify(app)

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<SecurityHubBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet(
    "recovery lost hardware - force exiting after spend key gen and before activating takes you back to activating"
  ) { initialApp ->
    var app = initialApp
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ACTIVATING_SPENDING_KEYS)
      cancelAndIgnoreRemainingEvents()
    }

    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ACTIVATING_SPENDING_KEYS)

      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet(
    "recovery lost hardware - force exiting after spend key gen and before ddk backup takes you back to DDK Backup"
  ) { initialApp ->
    var app = initialApp
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_DDK_UPLOAD)
      cancelAndIgnoreRemainingEvents()
    }

    // Force exit app on backup step
    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Resume on backup step and complete recovery
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet(
    "recovery lost hardware - force exiting after ddk tap and before ddk backup takes you back to DDK Backup"
  ) { initialApp ->
    var app = initialApp
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_DDK_UPLOAD)
      // DDK NFC completes instantly for fakes

      cancelAndIgnoreRemainingEvents()
    }

    // Force exit app after DDK tap
    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Resume on backup step and complete recovery
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet(
    "recovery lost hardware - force exiting after cloud backup & before sweep takes you back to sweep"
  ) { initialApp ->
    var app = initialApp
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_DDK_UPLOAD)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      cancelAndIgnoreRemainingEvents()
    }

    // Force exit app on sweep step
    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Resume on sweep step and complete recovery
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("recovery lost hardware - force exiting during D&N wait") { initialApp ->
    var app = initialApp
    // Use longer D&N duration to prevent race condition during app relaunch
    app.prepareApp(delayNotifyDuration = 5.seconds)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Go to Bitkey device settings
      awaitUntilBody<MoneyHomeBodyModel>()
        .onSecurityHubTabClick()
      awaitUntilBody<SecurityHubBodyModel>()
        .clickBitkeyDevice()
      awaitUntilBody<DeviceSettingsFormBodyModel>()
        .onReplaceDevice()

      // Initiate recovery
      awaitUntilBody<HardwareReplacementInstructionsModel>()
        .onContinue()
      awaitUntilBody<NewDeviceReadyQuestionBodyModel>()
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS_V2)
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
      awaitUntilBody<HardwareDelayNotifyInProgressScreenModel>()

      cancelAndIgnoreRemainingEvents()
    }

    // Force exit app during wait period
    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Go to Bitkey device settings
      awaitUntilBody<MoneyHomeBodyModel>()
        .onSecurityHubTabClick()
      awaitUntilBody<SecurityHubBodyModel>()
        .clickBitkeyDevice()
      awaitUntilBody<DeviceSettingsFormBodyModel>()
        .onManageReplacement.shouldNotBeNull().invoke()

      // Resume on delay in progress step
      awaitUntilBody<HardwareDelayNotifyInProgressScreenModel>()

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_DDK_UPLOAD)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<SecurityHubBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
  }

  testForRecoveryTransitions(
    "W3 recovery - force-exit before cloud backup resumes on cloud backup step",
    transitions = listOf(RecoveryHardwareTransition.W3ToW3)
  ) { transition ->
    var app = prepareW3App(transition)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      startW3RecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitW3ConfirmableNfcSession()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitW3ConfirmableNfcSession()
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
    }

    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(transition.replacementHardwareType)
    app.verifyPostRecoveryState()
  }

  testForRecoveryTransitions(
    "W3 recovery - force-exit after spending-key activation resumes at D&N ready",
    transitions = listOf(RecoveryHardwareTransition.W3ToW3)
  ) { transition ->
    var app = prepareW3App(transition)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      startW3RecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitW3ConfirmableNfcSession()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      cancelAndIgnoreRemainingEvents()
    }

    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // W3 spending-key NFC isn't checkpointed — resume goes back to D&N ready
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitW3ConfirmableNfcSession()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitW3ConfirmableNfcSession()
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(transition.replacementHardwareType)
    app.verifyPostRecoveryState()
  }

  testForRecoveryTransitions(
    "W3 recovery - force-exit after DDK upload replays NFC before cloud backup",
    transitions = listOf(RecoveryHardwareTransition.W3ToW3)
  ) { transition ->
    var app = prepareW3App(transition)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      startW3RecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitW3ConfirmableNfcSession()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitW3ConfirmableNfcSession()
      cancelAndIgnoreRemainingEvents()
    }

    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 60.seconds
    ) {
      // W3 DDK NFC isn't individually checkpointed — resume replays NFC sessions
      awaitW3ConfirmableNfcSession()
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(transition.replacementHardwareType)
    app.verifyPostRecoveryState()
  }
})
