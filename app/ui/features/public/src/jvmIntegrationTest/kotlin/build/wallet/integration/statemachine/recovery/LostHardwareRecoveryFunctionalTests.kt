package build.wallet.integration.statemachine.recovery

import app.cash.turbine.ReceiveTurbine
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.*
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.feature.setFlagValue
import build.wallet.integration.statemachine.recovery.cloud.screenDecideIfShouldRotate
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.recovery.cloud.CloudBackupFoundModel
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyScreens
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyScreens.DeactivateDevicesAfterRestoreChoice
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionBodyModel
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.inprogress.waiting.HardwareDelayNotifyInProgressScreenModel
import build.wallet.statemachine.recovery.sweep.SweepFundsPromptBodyModel
import build.wallet.statemachine.recovery.sweep.SweepSuccessScreenBodyModel
import build.wallet.statemachine.recovery.sweep.ZeroBalancePromptBodyModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.ui.model.alert.ButtonAlertModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class LostHardwareRecoveryFunctionalTests : FunSpec({
  suspend fun AppTester.prepareApp() {
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)
    onboardFullAccountWithFakeHardware()
    fakeNfcCommands.wipeDevice()
  }

  suspend fun AppTester.relaunchAppForLostHardware(): AppTester {
    return relaunchApp().also { relaunched ->
      relaunched.encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)
    }
  }

  testForLegacyAndPrivateWallet("lost hardware recovery - happy path") { app ->
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
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
    app = app.relaunchAppForLostHardware()

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
      awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        .clickPrimaryButton()
      // Initiating NFC
      awaitUntilBody<NfcBodyModel>()
      // Detected NFC
      awaitUntilBody<NfcBodyModel>()
      // Success NFC
      awaitUntilBody<NfcBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    app = app.relaunchAppForLostHardware()

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

    app = app.relaunchAppForLostHardware()

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
    app = app.relaunchAppForLostHardware()

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
      // Initiating NFC
      awaitUntilBody<NfcBodyModel>()
      // Detected NFC
      awaitUntilBody<NfcBodyModel>()
      // Success NFC
      awaitUntilBody<NfcBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    // Force exit app on backup step
    app = app.relaunchAppForLostHardware()

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
    app = app.relaunchAppForLostHardware()

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
    app.prepareApp()

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
      awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
      awaitUntilBody<HardwareDelayNotifyInProgressScreenModel>()

      cancelAndIgnoreRemainingEvents()
    }

    // Force exit app during wait period
    app = app.relaunchAppForLostHardware()

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

  testForLegacyAndPrivateWallet("recover lost hardware - sweep real funds") { initialApp ->
    val app = initialApp
    app.prepareApp()
    app.addSomeFunds(sats(10_000L))

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
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
      awaitUntilBody<SweepFundsPromptBodyModel>()
        .onSubmit()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING)
      awaitUntilBody<SweepSuccessScreenBodyModel>()
        .onDone()

      awaitUntilBody<SecurityHubBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
    app.waitForFunds()
    app.returnFundsToTreasury()
  }

  testForLegacyAndPrivateWallet("can Lost App from Cloud recovery then Lost Hardware recovery with funds") { initialApp ->
    val app = initialApp

    app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    // Fund wallet with some funds
    app.addSomeFunds()

    // Create new blank app, persist cloud backups
    val newApp = if (app.appMode == AppMode.Private) {
      launchNewApp(
        cloudStoreAccountRepository = app.cloudStoreAccountRepository,
        cloudKeyValueStore = app.cloudKeyValueStore,
        hardwareSeed = app.fakeHardwareKeyStore.getSeed()
      )
    } else {
      launchLegacyWalletApp(
        cloudStoreAccountRepository = app.cloudStoreAccountRepository,
        cloudKeyValueStore = app.cloudKeyValueStore,
        hardwareSeed = app.fakeHardwareKeyStore.getSeed()
      )
    }

    // Lost App recovery from Cloud
    newApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Complete cloud recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudBackupFoundModel>()
        .onRestore()
      awaitUntilBody<DeactivateDevicesAfterRestoreChoice>(
        matching = { it.removeAllOtherDevicesEnabled }
      ).onRemoveAllOtherDevices()
      awaitUntilBody<RotateAuthKeyScreens.Confirmation>()
        .onSelected()
      newApp.waitForFunds()
      awaitUntilBody<MoneyHomeBodyModel>(
        matching = { it.balanceModel.secondaryAmount != "0 sats" }
      )
      newApp.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    newApp.verifyPostRecoveryState()
    newApp.fakeNfcCommands.wipeDevice()

    // Complete Lost Hardware Recovery with D&N
    newApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
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
      awaitUntilBody<SweepFundsPromptBodyModel>()
        .onSubmit()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING)
      awaitUntilBody<SweepSuccessScreenBodyModel>()
        .onDone()

      awaitUntilBody<SecurityHubBodyModel>()
      newApp.awaitNoActiveRecovery()

      newApp.waitForFunds()
      newApp.returnFundsToTreasury()

      cancelAndIgnoreRemainingEvents()
    }
    newApp.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("can Lost Hardware recovery then Lost App recovery from Cloud with funds") { initialApp ->
    val app = initialApp
    app.prepareApp()
    // Fund wallet with some funds
    app.addSomeFunds()

    // Complete Lost Hardware Recovery with D&N
    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)

      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<SweepFundsPromptBodyModel>()
        .onSubmit()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING)
      awaitUntilBody<SweepSuccessScreenBodyModel>()
        .onDone()

      awaitUntilBody<SecurityHubBodyModel>()
      app.awaitNoActiveRecovery()

      app.waitForFunds()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
    // Create new blank app, persist cloud backups, keep hardware
    val newApp = if (app.appMode == AppMode.Private) {
      launchNewApp(
        cloudStoreAccountRepository = app.cloudStoreAccountRepository,
        cloudKeyValueStore = app.cloudKeyValueStore,
        hardwareSeed = app.fakeHardwareKeyStore.getSeed()
      )
    } else {
      launchLegacyWalletApp(
        cloudStoreAccountRepository = app.cloudStoreAccountRepository,
        cloudKeyValueStore = app.cloudKeyValueStore,
        hardwareSeed = app.fakeHardwareKeyStore.getSeed()
      )
    }

    // Lost App recovery from Cloud
    newApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudBackupFoundModel>()
        .onRestore()
      screenDecideIfShouldRotate {
        clickPrimaryButton()
      }
      newApp.waitForFunds()
      awaitUntilBody<MoneyHomeBodyModel>(
        matching = { it.balanceModel.secondaryAmount != "0 sats" }
      )
      newApp.returnFundsToTreasury()

      cancelAndIgnoreRemainingEvents()
    }
    newApp.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("cancel initiated recovery") { initialApp ->
    val app = initialApp
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      // Cancel recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>(
        matching = { it.onStopRecovery != null }
      ).onStopRecovery.shouldNotBeNull().invoke()

      awaitUntilScreenWithBody<DelayAndNotifyNewKeyReady>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
      }

      awaitUntilBody<SecurityHubBodyModel>()

      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("lost hardware recovery refreshes descriptor backups if enabled") {
    val app = launchLegacyWalletApp()
    app.encryptedDescriptorBackupsFeatureFlag.setFlagValue(false)
    app.onboardFullAccountWithFakeHardware()

    val accountId = app.getActiveFullAccount().accountId

    app.verifyNoDescriptorBackups(accountId)
    app.verifyCanUseKeyboxKeysets(true)

    app.fakeNfcCommands.wipeDevice()
    app.encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)

    app.performLostHardwareRecovery()

    app.verifyDescriptorBackupsUploaded(accountId, count = 2)
    app.verifyCanUseKeyboxKeysets(true)
    app.decryptCloudBackupKeys().keysets.size.shouldBe(2)
  }
})

suspend fun AppTester.performLostHardwareRecovery() {
  appUiStateMachine.test(
    props = Unit,
    testTimeout = 60.seconds,
    turbineTimeout = 60.seconds
  ) {
    startRecoveryAndAdvanceToDelayNotify(this@performLostHardwareRecovery)

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
    awaitNoActiveRecovery()

    cancelAndIgnoreRemainingEvents()
  }
}

private suspend fun ReceiveTurbine<ScreenModel>.startRecoveryAndAdvanceToDelayNotify(
  app: AppTester,
) {
  app.awaitNoActiveRecovery()

  // Go to Bitkey device settings via Security Hub
  awaitUntilBody<MoneyHomeBodyModel>()
    .onSecurityHubTabClick()
  awaitUntilBody<SecurityHubBodyModel>()
    .clickBitkeyDevice()

  // Initiate recovery
  awaitUntilBody<DeviceSettingsFormBodyModel>()
    .onReplaceDevice()
  awaitUntilBody<HardwareReplacementInstructionsModel>()
    .onContinue()
  awaitUntilBody<NewDeviceReadyQuestionBodyModel>()
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
}
