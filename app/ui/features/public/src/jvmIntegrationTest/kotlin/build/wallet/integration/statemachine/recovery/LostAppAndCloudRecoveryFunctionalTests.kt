package build.wallet.integration.statemachine.recovery

import app.cash.turbine.ReceiveTurbine
import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.*
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.platform.permissions.EnableNotificationsBodyModel
import build.wallet.statemachine.recovery.cloud.CloudWarningBodyModel
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.inprogress.RecoverYourAppKeyBodyModel
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.recovery.sweep.SweepFundsPromptBodyModel
import build.wallet.statemachine.recovery.sweep.SweepSuccessScreenBodyModel
import build.wallet.statemachine.recovery.sweep.ZeroBalancePromptBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.ext.HardwareCoverageMode
import build.wallet.testing.ext.assertActiveHardwareType
import build.wallet.testing.ext.*
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class LostAppAndCloudRecoveryFunctionalTests : FunSpec({
  suspend fun AppTester.setupForLostApp(
    initWithTreasuryFunds: BitcoinMoney = BitcoinMoney.zero(),
    delayNotifyDuration: kotlin.time.Duration = 5.seconds,
    hardwareType: bitkey.account.HardwareType = bitkey.account.HardwareType.W1,
  ) {
    onboardFullAccountWithFakeHardware(
      delayNotifyDuration = delayNotifyDuration,
      hardwareType = hardwareType
    )
    if (initWithTreasuryFunds != BitcoinMoney.zero()) {
      val wallet = getActiveWallet()
      treasuryWallet.fund(wallet, initWithTreasuryFunds)
    }
    appDataDeleter.deleteAll().getOrThrow()
    val accountId = FullAccountIdMock
    cloudBackupDeleter.delete(accountId)
    deleteBackupsFromFakeCloud(accountId)
  }

  suspend fun AppTester.relaunchForLostApp(
    delayNotifyDuration: kotlin.time.Duration = 5.seconds,
  ): AppTester {
    return relaunchApp().also { relaunched ->
      relaunched.defaultAccountConfigService.setDelayNotifyDuration(delayNotifyDuration)
    }
  }

  testForHardwareHappyPaths("delay & notify - no cloud backup") { app, coverageMode ->
    app.setupForLostApp(hardwareType = coverageMode.hardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Attempt to sign in to cloud but no backup
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate Delay & Notify recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Complete recovery
      awaitUntilRotatingAuthAfterDelayNotifyReady(coverageMode.hardwareType)
      advanceToSaveBackupInstructionsAfterDelayNotify(coverageMode.hardwareType)
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
    app.verifyPostRecoveryState()
  }

  testForHardwareHappyPaths("delay & notify - no cloud access") { app, coverageMode ->
    app.setupForLostApp(hardwareType = coverageMode.hardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Failing to sign in to cloud
      awaitUntilBody<CloudSignInModelFake>()
        .signInFailure(Error())
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate Delay & Notify recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Complete recovery
      awaitUntilRotatingAuthAfterDelayNotifyReady(coverageMode.hardwareType)
      advanceToSaveBackupInstructionsAfterDelayNotify(coverageMode.hardwareType)
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)

      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
    app.verifyPostRecoveryState()
  }

  testForHardwareHappyPaths("recovery lost app - force exiting in the middle of initiating") { initialApp, coverageMode ->
    var app = initialApp
    app.setupForLostApp(hardwareType = coverageMode.hardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Attempt to sign in to cloud but no backup
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate Delay & Notify recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    app = app.relaunchForLostApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Resume with Delay & Notify period in progress
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Complete recovery
      awaitUntilRotatingAuthAfterDelayNotifyReady(coverageMode.hardwareType)
      advanceToSaveBackupInstructionsAfterDelayNotify(coverageMode.hardwareType)
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }
    app.assertActiveHardwareType(coverageMode.hardwareType)
    app.verifyPostRecoveryState()
  }

  testForHardwareHappyPaths("force exiting before spending key activation takes you back to spending key activation") { initialApp, coverageMode ->
    var app = initialApp
    app.setupForLostApp(hardwareType = coverageMode.hardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Attempt to sign in to cloud but no backup
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate Delay & Notify recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Start completing recovery
      awaitUntilRotatingAuthAfterDelayNotifyReady(coverageMode.hardwareType)
      if (coverageMode.hardwareType == HardwareType.W3) {
        awaitUntilScreenWithBody<BodyModel>(
          matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
        ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
        awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
      }
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ACTIVATING_SPENDING_KEYS)
    }

    app = app.relaunchForLostApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      if (coverageMode.hardwareType == HardwareType.W3) {
        awaitUntilScreenWithBody<BodyModel>(
          matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
        ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
        awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
      }
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ACTIVATING_SPENDING_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
    app.verifyPostRecoveryState()
  }

  testForHardwareHappyPaths("force exiting before cloud backup takes you back to icloud backup") { initialApp, coverageMode ->
    var app = initialApp
    app.setupForLostApp(hardwareType = coverageMode.hardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Attempt to sign in to cloud but no backup
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate Delay & Notify recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Start completing recovery
      awaitUntilRotatingAuthAfterDelayNotifyReady(coverageMode.hardwareType)
      advanceToSaveBackupInstructionsAfterDelayNotify(coverageMode.hardwareType)
    }

    // Force quit app before cloud backup was saved
    app = app.relaunchForLostApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Resume on cloud backup step and then sweep
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
    app.verifyPostRecoveryState()
  }

  testForHardwareHappyPaths("force exiting after cloud backup & before sweep takes you back to sweep") { initialApp, coverageMode ->
    var app = initialApp
    app.setupForLostApp(hardwareType = coverageMode.hardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Attempt to sign in to cloud but no backup
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate Delay & Notify recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Start completing recovery
      awaitUntilRotatingAuthAfterDelayNotifyReady(coverageMode.hardwareType)
      advanceToSaveBackupInstructionsAfterDelayNotify(coverageMode.hardwareType)
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)

      cancelAndIgnoreRemainingEvents()
    }

    // Force quit app after cloud backup was saved but before sweep
    app = app.relaunchForLostApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Resume on sweep step
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
    app.verifyPostRecoveryState()
  }

  testForHardwareHappyPaths("force exiting during D&N wait") { initialApp, coverageMode ->
    var app = initialApp
    app.setupForLostApp(hardwareType = coverageMode.hardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Attempt to sign in to cloud but no backup
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate Delay & Notify recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    // Force quit app during D&N wait
    app = app.relaunchForLostApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Resume on D&N wait
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Complete recovery
      awaitUntilRotatingAuthAfterDelayNotifyReady(coverageMode.hardwareType)
      advanceToSaveBackupInstructionsAfterDelayNotify(coverageMode.hardwareType)

      cancelAndIgnoreRemainingEvents()
    }
  }

  testForHardwareHappyPaths("ensure funds are swept after recovery") { app, coverageMode ->
    app.setupForLostApp(
      initWithTreasuryFunds = BitcoinMoney.sats(10_000),
      hardwareType = coverageMode.hardwareType
    )

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Attempt to sign in to cloud but no backup
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Complete recovery
      awaitUntilRotatingAuthAfterDelayNotifyReady(coverageMode.hardwareType)
      advanceToSaveBackupInstructionsAfterDelayNotify(coverageMode.hardwareType)
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<SweepFundsPromptBodyModel>()
        .onSubmit()
      if (coverageMode.hardwareType == HardwareType.W3) {
        awaitUntilScreenWithBody<BodyModel>(
          matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
        ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
        awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
      }
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING)
      awaitUntilBody<SweepSuccessScreenBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      app.waitForFunds()
      app.returnFundsToTreasury()
      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
    app.verifyPostRecoveryState()
  }

  testForHardwareHappyPaths("cancel initiated delay & notify recovery when delay period is in progress") { app, coverageMode ->
    app.setupForLostApp(hardwareType = coverageMode.hardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Attempt to sign in to cloud but no backup
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate Delay & Notify recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()
        .onStopRecovery()

      awaitUntilScreenWithBody<AppDelayNotifyInProgressBodyModel>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
      }

      if (coverageMode.hardwareType == HardwareType.W3) {
        awaitUntilScreenWithBody<BodyModel>(
          matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
        ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
        awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
      }

      awaitUntilBody<ChooseAccountAccessModel>()

      app.awaitNoActiveRecovery()
    }
  }

  testForHardwareHappyPaths("cancel initiated delay & notify recovery when delay period has finished") { app, coverageMode ->
    app.setupForLostApp(hardwareType = coverageMode.hardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      // Start recovery
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
        .onRestoreYourWalletClick()

      // Attempt to sign in to cloud but no backup
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudWarningBodyModel>()
        .onCannotAccessCloud()

      // Initiate Delay & Notify recovery
      awaitUntilBody<RecoverYourAppKeyBodyModel>()
        .onStartRecovery()
      advanceThroughDelayNotifyInitiation(coverageMode.hardwareType)
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      awaitUntilBody<DelayAndNotifyNewKeyReady>(
        matching = { it.onStopRecovery != null }
      ) {
        onStopRecovery.shouldNotBeNull().invoke()
      }

      awaitUntilScreenWithBody<DelayAndNotifyNewKeyReady>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
      }

      if (coverageMode.hardwareType == HardwareType.W3) {
        awaitUntilScreenWithBody<BodyModel>(
          matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
        ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
        awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
      }

      awaitUntilBody<ChooseAccountAccessModel>()

      app.awaitNoActiveRecovery()
    }
  }

  test("lost app recovery refreshes descriptor backups") {
    var app = launchLegacyWalletApp()
    app.setupForLostApp()
    app.onboardFullAccountWithFakeHardware(
      delayNotifyDuration = 5.seconds,
      shouldUploadDescriptorBackups = false
    )

    val accountId = app.getActiveFullAccount().accountId

    app.verifyNoDescriptorBackups(accountId)
    app.verifyCanUseKeyboxKeysets(true)

    app.appDataDeleter.deleteAll().getOrThrow()
    app.cloudBackupDeleter.delete(accountId)
    app.deleteBackupsFromFakeCloud(accountId)

    app = app.relaunchForLostApp()

    app.performRecovery()

    app.verifyDescriptorBackupsUploaded(accountId, 2)
    app.verifyCanUseKeyboxKeysets(true)
    app.decryptCloudBackupKeys().keysets.size.shouldBe(2)
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughDelayNotifyInitiation(
  hardwareType: HardwareType = HardwareType.W1,
) {
  if (hardwareType == HardwareType.W3) {
    // Sign auth challenge (confirmable NFC session)
    awaitUntilScreenWithBody<BodyModel>(
      matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
    ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
    awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
    // Composite lost app recovery (confirmable NFC session)
    awaitUntilScreenWithBody<BodyModel>(
      matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
    ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
    awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
  }
  awaitUntilBody<EnableNotificationsBodyModel> { onComplete() }
}

private suspend fun ReceiveTurbine<ScreenModel>.awaitUntilRotatingAuthAfterDelayNotifyReady(
  hardwareType: HardwareType = HardwareType.W1,
) {
  awaitUntilBody<DelayAndNotifyNewKeyReady> { onCompleteRecovery() }
  if (hardwareType == HardwareType.W3) {
    awaitUntilScreenWithBody<BodyModel>(
      matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
    ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
    awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
  }
  awaitUntilBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceToSaveBackupInstructionsAfterDelayNotify(
  hardwareType: HardwareType = HardwareType.W1,
): SaveBackupInstructionsBodyModel {
  if (hardwareType == HardwareType.W3) {
    awaitUntilScreenWithBody<BodyModel>(
      matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
    ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
    awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
  }
  return awaitUntilBody<SaveBackupInstructionsBodyModel>()
}

suspend fun AppTester.performRecovery() {
  appUiStateMachine.test(
    props = Unit,
    testTimeout = 20.seconds,
    turbineTimeout = 10.seconds
  ) {
    // Start recovery
    awaitUntilBody<ChooseAccountAccessModel>()
      .clickMoreOptionsButton()
    awaitUntilBody<AccountAccessMoreOptionsFormBodyModel>()
      .onRestoreYourWalletClick()

    // Failing to sign in to cloud
    awaitUntilBody<CloudSignInModelFake>()
      .signInFailure(Error())
    awaitUntilBody<CloudWarningBodyModel>()
      .onCannotAccessCloud()

    // Initiate Delay & Notify recovery
    awaitUntilBody<RecoverYourAppKeyBodyModel>()
      .onStartRecovery()
    advanceThroughDelayNotifyInitiation()

    // Complete recovery
    awaitUntilRotatingAuthAfterDelayNotifyReady()
    advanceToSaveBackupInstructionsAfterDelayNotify()
      .onBackupClick()
    awaitUntilBody<CloudSignInModelFake>()
      .signInSuccess(CloudStoreAccount1Fake)

    awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
    awaitUntilBody<ZeroBalancePromptBodyModel>()
      .onDone()

    awaitUntilBody<MoneyHomeBodyModel>()
    awaitNoActiveRecovery()

    cancelAndIgnoreRemainingEvents()
  }
}
