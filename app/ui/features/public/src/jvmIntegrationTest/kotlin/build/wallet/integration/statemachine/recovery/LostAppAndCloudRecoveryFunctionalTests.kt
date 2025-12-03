package build.wallet.integration.statemachine.recovery

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.*
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.platform.permissions.EnableNotificationsBodyModel
import build.wallet.statemachine.recovery.cloud.CloudWarningBodyModel
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.inprogress.RecoverYourAppKeyBodyModel
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.recovery.sweep.SweepFundsPromptBodyModel
import build.wallet.statemachine.recovery.sweep.SweepSuccessScreenBodyModel
import build.wallet.statemachine.recovery.sweep.ZeroBalancePromptBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
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
  ) {
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)
    onboardFullAccountWithFakeHardware(delayNotifyDuration = delayNotifyDuration)
    if (initWithTreasuryFunds != BitcoinMoney.zero()) {
      val wallet = getActiveWallet()
      treasuryWallet.fund(wallet, initWithTreasuryFunds)
    }
    appDataDeleter.deleteAll().getOrThrow()
    cloudBackupDeleter.delete()
    deleteBackupsFromFakeCloud()
  }

  suspend fun AppTester.relaunchForLostApp(
    delayNotifyDuration: kotlin.time.Duration = 5.seconds,
  ): AppTester {
    return relaunchApp().also { relaunched ->
      relaunched.encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)
      relaunched.defaultAccountConfigService.setDelayNotifyDuration(delayNotifyDuration)
    }
  }

  testForLegacyAndPrivateWallet("delay & notify - no cloud backup") { app ->
    app.setupForLostApp()

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
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

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("delay & notify - no cloud access") { app ->
    app.setupForLostApp()

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
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

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("recovery lost app - force exiting in the middle of initiating") { initialApp ->
    var app = initialApp
    app.setupForLostApp()

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
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
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
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
    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("force exiting before spending key activation takes you back to spending key activation") { initialApp ->
    var app = initialApp
    app.setupForLostApp()

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Start completing recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ACTIVATING_SPENDING_KEYS)
    }

    app = app.relaunchForLostApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
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

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("force exiting before cloud backup takes you back to icloud backup") { initialApp ->
    var app = initialApp
    app.setupForLostApp()

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Start completing recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
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

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("force exiting after cloud backup & before sweep takes you back to sweep") { initialApp ->
    var app = initialApp
    app.setupForLostApp()

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Start completing recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
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

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("force exiting during D&N wait") { initialApp ->
    var app = initialApp
    app.setupForLostApp()

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
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
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  testForLegacyAndPrivateWallet("ensure funds are swept after recovery") { app ->
    app.setupForLostApp(initWithTreasuryFunds = BitcoinMoney.sats(10_000))

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<SweepFundsPromptBodyModel>()
        .onSubmit()
      awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING)
      awaitUntilBody<SweepSuccessScreenBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()
      app.awaitNoActiveRecovery()

      app.waitForFunds()
      app.returnFundsToTreasury()
      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("cancel initiated delay & notify recovery when delay period is in progress") { app ->
    app.setupForLostApp()

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>()
        .onStopRecovery()

      awaitUntilScreenWithBody<AppDelayNotifyInProgressBodyModel>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
      }

      awaitUntilBody<ChooseAccountAccessModel>()

      app.awaitNoActiveRecovery()
    }
  }

  testForLegacyAndPrivateWallet("cancel initiated delay & notify recovery when delay period has finished") { app ->
    app.setupForLostApp()

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
      awaitUntilBody<EnableNotificationsBodyModel>()
        .onComplete()
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

      awaitUntilBody<ChooseAccountAccessModel>()

      app.awaitNoActiveRecovery()
    }
  }

  test("lost app recovery refreshes descriptor backups if enabled") {
    var app = launchLegacyWalletApp()
    app.setupForLostApp()
    app.encryptedDescriptorBackupsFeatureFlag.setFlagValue(false)
    app.onboardFullAccountWithFakeHardware(delayNotifyDuration = 5.seconds)

    val accountId = app.getActiveFullAccount().accountId

    app.verifyNoDescriptorBackups(accountId)
    app.verifyCanUseKeyboxKeysets(true)

    app.appDataDeleter.deleteAll().getOrThrow()
    app.cloudBackupDeleter.delete()
    app.deleteBackupsFromFakeCloud()

    app = app.relaunchForLostApp()
    app.encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)

    app.performRecovery()

    app.verifyDescriptorBackupsUploaded(accountId, 2)
    app.verifyCanUseKeyboxKeysets(true)
    app.decryptCloudBackupKeys().keysets.size.shouldBe(2)
  }
})

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
    awaitUntilBody<EnableNotificationsBodyModel>()
      .onComplete()

    // Complete recovery
    awaitUntilBody<DelayAndNotifyNewKeyReady>()
      .onCompleteRecovery()
    awaitLoadingScreen(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
    awaitUntilBody<SaveBackupInstructionsBodyModel>()
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
