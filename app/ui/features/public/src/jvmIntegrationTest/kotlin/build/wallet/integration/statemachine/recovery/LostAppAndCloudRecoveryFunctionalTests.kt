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
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class LostAppAndCloudRecoveryFunctionalTests : FunSpec({
  lateinit var app: AppTester

  suspend fun TestScope.setup(initWithTreasuryFunds: BitcoinMoney = BitcoinMoney.zero()) {
    app = launchNewApp()
    app.encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)
    app.onboardFullAccountWithFakeHardware(delayNotifyDuration = 5.seconds)
    if (initWithTreasuryFunds != BitcoinMoney.zero()) {
      val wallet = app.getActiveWallet()
      app.treasuryWallet.fund(wallet, initWithTreasuryFunds)
    }
    app.appDataDeleter.deleteAll().getOrThrow()
    app.cloudBackupDeleter.delete()
    app.deleteBackupsFromFakeCloud()
  }

  suspend fun relaunchApp() {
    app = app.relaunchApp()
    app.encryptedDescriptorBackupsFeatureFlag.setFlagValue(true)
    app.defaultAccountConfigService.setDelayNotifyDuration(5.seconds)
  }

  test("delay & notify - no cloud backup") {
    setup()

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
  }

  test("delay & notify - no cloud access") {
    setup()

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
  }
  test("recovery lost app - force exiting in the middle of initiating") {
    setup()

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

    relaunchApp()

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
  }

  test("force exiting before cloud backup takes you back to icloud backup") {
    setup()

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

    // Force quite app before cloud backup was saved
    relaunchApp()

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
  }

  test("force exiting after cloud backup & before sweep takes you back to sweep") {
    setup()

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

    // Force quite app after cloud backup was saved but before sweep
    relaunchApp()

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
  }

  test("force exiting during D&N wait") {
    setup()

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

    // Force quite app during D&N wait
    relaunchApp()

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

  test("ensure funds are swept after recovery") {
    setup(BitcoinMoney.sats(10_000))

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
  }

  test("cancel initiated delay & notify recovery when delay period is in progress") {
    setup()

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

  test("cancel initiated delay & notify recovery when delay period has finished") {
    setup()

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
})
