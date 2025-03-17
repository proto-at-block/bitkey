package build.wallet.integration.statemachine.recovery

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.integration.statemachine.recovery.RecoveryTestingTrackerScreenId.RECOVERY_COMPLETED
import build.wallet.money.BitcoinMoney
import build.wallet.money.matchers.shouldBeGreaterThan
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.FlakyTest
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class LostAppAndCloudRecoveryFunctionalTests : FunSpec({
  lateinit var app: AppTester
  lateinit var recoveryStateMachine: RecoveryTestingStateMachine

  suspend fun TestScope.setup(initWithTreasuryFunds: BitcoinMoney = BitcoinMoney.zero()) {
    app = launchNewApp()
    app.onboardFullAccountWithFakeHardware(delayNotifyDuration = 2.seconds)
    if (initWithTreasuryFunds != BitcoinMoney.zero()) {
      val wallet = app.getActiveWallet()
      app.treasuryWallet.fund(wallet, initWithTreasuryFunds)
    }
    app.appDataDeleter.deleteAll().getOrThrow()
    app.cloudBackupDeleter.delete()
    app.deleteBackupsFromFakeCloud()
    recoveryStateMachine =
      RecoveryTestingStateMachine(
        app.accountDataStateMachine,
        app.recoveringKeyboxUiStateMachine,
        app.recoverySyncer,
        app.accountService
      )
  }

  suspend fun resetApp() {
    app = app.relaunchApp()
    app.defaultAccountConfigService.setDelayNotifyDuration(2.seconds)
    recoveryStateMachine =
      RecoveryTestingStateMachine(
        app.accountDataStateMachine,
        app.recoveringKeyboxUiStateMachine,
        app.recoverySyncer,
        app.accountService
      )
  }

  test("delay & notify")
    .config(tags = setOf(FlakyTest)) {
      setup()
      app.apply {
        recoveryStateMachine.test(
          props = Unit,
          testTimeout = 20.seconds,
          turbineTimeout = 10.seconds
        ) {
          awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
            .clickPrimaryButton()
          awaitUntilBody<FormBodyModel>(ENABLE_PUSH_NOTIFICATIONS)
            .clickPrimaryButton()
          awaitUntilBody<AppDelayNotifyInProgressBodyModel>(LOST_APP_DELAY_NOTIFY_PENDING)

          app.completeRecoveryDelayPeriodOnF8e()
          awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_READY)
            .clickPrimaryButton()
          awaitUntilBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
            state.shouldBe(LoadingSuccessBodyModel.State.Loading)
          }
          awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
            .clickPrimaryButton()
          awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
            .signInSuccess(CloudStoreAccount1Fake)
          awaitUntilBody<LoadingSuccessBodyModel>(
            LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
          ) {
            state.shouldBe(LoadingSuccessBodyModel.State.Loading)
          }
          awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
            .clickPrimaryButton()
          awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
          cancelAndIgnoreRemainingEvents()
        }
      }
    }

  test("recovery lost app - force exiting in the middle of initiating") {
    setup()
    app.apply {
      recoveryStateMachine.test(
        props = Unit,
        testTimeout = 20.seconds,
        turbineTimeout = 10.seconds
      ) {
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(ENABLE_PUSH_NOTIFICATIONS)
          .clickPrimaryButton()
        cancelAndIgnoreRemainingEvents()
      }

      resetApp()

      recoveryStateMachine.test(
        props = Unit,
        testTimeout = 20.seconds,
        turbineTimeout = 10.seconds
      ) {
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(ENABLE_PUSH_NOTIFICATIONS)
          .clickPrimaryButton()
        awaitUntilBody<AppDelayNotifyInProgressBodyModel>(LOST_APP_DELAY_NOTIFY_PENDING)

        app.completeRecoveryDelayPeriodOnF8e()
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
          .signInSuccess(CloudStoreAccount1Fake)
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test("force exiting before cloud backup takes you back to icloud backup") {
    setup()
    app.apply {
      recoveryStateMachine.test(
        props = Unit,
        testTimeout = 20.seconds,
        turbineTimeout = 10.seconds
      ) {
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(ENABLE_PUSH_NOTIFICATIONS)
          .clickPrimaryButton()
        awaitUntilBody<AppDelayNotifyInProgressBodyModel>(LOST_APP_DELAY_NOTIFY_PENDING)

        app.completeRecoveryDelayPeriodOnF8e()
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
      }

      // Force quit!
      resetApp()

      recoveryStateMachine.test(
        props = Unit,
        testTimeout = 20.seconds,
        turbineTimeout = 10.seconds
      ) {
        awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
          .signInSuccess(CloudStoreAccount1Fake)
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test("force exiting after cloud backup & before sweep takes you back to sweep") {
    setup()
    app.apply {
      recoveryStateMachine.test(
        props = Unit,
        testTimeout = 20.seconds,
        turbineTimeout = 10.seconds
      ) {
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(ENABLE_PUSH_NOTIFICATIONS)
          .clickPrimaryButton()
        awaitUntilBody<AppDelayNotifyInProgressBodyModel>(LOST_APP_DELAY_NOTIFY_PENDING)

        app.completeRecoveryDelayPeriodOnF8e()
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
          .signInSuccess(CloudStoreAccount1Fake)
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        cancelAndIgnoreRemainingEvents()
      }

      resetApp()

      recoveryStateMachine.test(
        props = Unit,
        testTimeout = 20.seconds,
        turbineTimeout = 10.seconds
      ) {
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test("force exiting during D&N wait") {
    setup()
    app.apply {
      recoveryStateMachine.test(
        props = Unit,
        testTimeout = 20.seconds,
        turbineTimeout = 10.seconds
      ) {
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(ENABLE_PUSH_NOTIFICATIONS)
          .clickPrimaryButton()
        awaitUntilBody<AppDelayNotifyInProgressBodyModel>(LOST_APP_DELAY_NOTIFY_PENDING)
        cancelAndIgnoreRemainingEvents()
      }

      resetApp()

      recoveryStateMachine.test(
        props = Unit,
        testTimeout = 20.seconds,
        turbineTimeout = 10.seconds
      ) {
        awaitUntilBody<AppDelayNotifyInProgressBodyModel>(LOST_APP_DELAY_NOTIFY_PENDING)

        app.completeRecoveryDelayPeriodOnF8e()
        awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test("ensure funds are swept after recovery") {
    setup(BitcoinMoney.sats(10_000))

    recoveryStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<FormBodyModel>(ENABLE_PUSH_NOTIFICATIONS)
        .clickPrimaryButton()
      awaitUntilBody<AppDelayNotifyInProgressBodyModel>(LOST_APP_DELAY_NOTIFY_PENDING)

      app.completeRecoveryDelayPeriodOnF8e()
      awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT)
        .clickPrimaryButton()

      awaitUntilBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitUntilBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS)
        .clickPrimaryButton()

      awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)

      eventually(
        eventuallyConfig {
          duration = 20.seconds
          interval = 1.seconds
          initialDelay = 1.seconds
        }
      ) {
        val activeWallet = app.getActiveWallet()
        activeWallet.sync().shouldBeOk()
        val balance = activeWallet.balance().first()
        balance.total.shouldBeGreaterThan(BitcoinMoney.sats(0))
        // Eventually could iterate to calculate and subtract psbtsGeneratedData.totalFeeAmount)
        app.returnFundsToTreasury()
      }
    }
  }
})
