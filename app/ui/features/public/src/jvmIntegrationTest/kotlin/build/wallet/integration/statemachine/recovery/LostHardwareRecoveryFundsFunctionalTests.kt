package build.wallet.integration.statemachine.recovery

import app.cash.turbine.ReceiveTurbine
import bitkey.account.HardwareType
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.*
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.integration.statemachine.recovery.cloud.screenDecideIfShouldRotate
import build.wallet.integration.statemachine.recovery.socrec.awaitW3ConfirmableNfcSession
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.recovery.cloud.CloudBackupFoundModel
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyScreens
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyScreens.DeactivateDevicesAfterRestoreChoice
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionBodyModel
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
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
import build.wallet.testing.ext.RecoveryHardwareTransition
import build.wallet.testing.ext.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LostHardwareRecoveryFundsFunctionalTests : FunSpec({
  suspend fun AppTester.prepareApp(delayNotifyDuration: Duration = 1.seconds) {
    onboardFullAccountWithFakeHardware(delayNotifyDuration = delayNotifyDuration)
    fakeNfcCommands.wipeDevice()
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

  // --- W3 Hardware Transition Tests ---

  testForRecoveryTransitions(
    "recover lost hardware - sweep real funds",
    transitions = listOf(RecoveryHardwareTransition.W3ToW3)
  ) { transition ->
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware(hardwareType = transition.sourceHardwareType)
    app.fakeW3NfcCommands.wipeDevice()
    app.defaultAccountConfigService.setHardwareType(transition.replacementHardwareType)
    app.addSomeFunds(sats(10_000L))

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      startW3RecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      // W3: confirmable NFC session for spending key creation
      awaitW3ConfirmableNfcSession()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      // W3: confirmable NFC session for DDK upload
      awaitW3ConfirmableNfcSession()
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

    app.assertActiveHardwareType(transition.replacementHardwareType)
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
        cloudBackupStore = app.cloudBackupStore,
        hardwareSeed = app.fakeHardwareKeyStore.getSeed()
      )
    } else {
      launchLegacyWalletApp(
        cloudStoreAccountRepository = app.cloudStoreAccountRepository,
        cloudBackupStore = app.cloudBackupStore,
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
      awaitUntilBody<MoneyHomeBodyModel>()
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
        cloudBackupStore = app.cloudBackupStore,
        hardwareSeed = app.fakeHardwareKeyStore.getSeed()
      )
    } else {
      launchLegacyWalletApp(
        cloudStoreAccountRepository = app.cloudStoreAccountRepository,
        cloudBackupStore = app.cloudBackupStore,
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
      awaitUntilBody<MoneyHomeBodyModel>()
      newApp.returnFundsToTreasury()

      cancelAndIgnoreRemainingEvents()
    }
    newApp.verifyPostRecoveryState()
  }

  test("lost hardware recovery refreshes descriptor backups") {
    val app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware(shouldUploadDescriptorBackups = false)

    val accountId = app.getActiveFullAccount().accountId

    app.verifyNoDescriptorBackups(accountId)
    app.verifyCanUseKeyboxKeysets(true)

    app.fakeNfcCommands.wipeDevice()

    app.performLostHardwareRecovery()

    app.verifyDescriptorBackupsUploaded(accountId, count = 2)
    app.verifyCanUseKeyboxKeysets(true)
    app.decryptCloudBackupKeys().keysets.size.shouldBe(2)
  }
})
