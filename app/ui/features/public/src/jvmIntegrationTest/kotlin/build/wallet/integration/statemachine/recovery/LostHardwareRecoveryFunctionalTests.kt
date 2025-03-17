package build.wallet.integration.statemachine.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.cash.turbine.ReceiveTurbine
import bitkey.account.FullAccountConfig
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.*
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.integration.statemachine.recovery.RecoveryTestingTrackerScreenId.*
import build.wallet.integration.statemachine.recovery.cloud.screenDecideIfShouldRotate
import build.wallet.keybox.KeyboxDao
import build.wallet.money.BitcoinMoney
import build.wallet.money.matchers.shouldBeGreaterThan
import build.wallet.recovery.Recovery.Loading
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoverySyncer
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountDataProps
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.recovery.losthardware.initiate.InstructionsStyle
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class LostHardwareRecoveryFunctionalTests : FunSpec({
  data class Props(val fullAccountConfig: FullAccountConfig, val originalKeyboxId: String)

  class TestingStateMachine(
    val dsm: AccountDataStateMachine,
    val usm: LostHardwareRecoveryUiStateMachine,
    val keyboxDao: KeyboxDao,
    val recoverySyncer: RecoverySyncer,
  ) : StateMachine<Props, ScreenModel> {
    @Composable
    override fun model(props: Props): ScreenModel {
      val activeKeybox =
        remember {
          keyboxDao.activeKeybox()
        }.collectAsState(null).value?.getOrThrow()
      val activeRecovery =
        remember { recoverySyncer.recoveryStatus() }.collectAsState(Ok(Loading))
          .value.getOrThrow()
      val (aborted, updateAborted) = remember { mutableStateOf(false) }
      if (aborted) {
        preStartOrPostRecoveryCompletionScreen(RECOVERY_ABORTED)
      }
      if (props.originalKeyboxId != activeKeybox?.localId && activeRecovery == NoActiveRecovery) {
        return preStartOrPostRecoveryCompletionScreen(RECOVERY_COMPLETED)
      }
      val accountData = dsm.model(AccountDataProps { })
      return when (accountData) {
        is CheckingActiveAccountData,
        -> preStartOrPostRecoveryCompletionScreen(RECOVERY_NOT_STARTED)
        is ActiveFullAccountLoadedData -> {
          usm.model(
            LostHardwareRecoveryProps(
              account = accountData.account,
              lostHardwareRecoveryData = accountData.lostHardwareRecoveryData,
              screenPresentationStyle = Root,
              onFoundHardware = {},
              instructionsStyle = InstructionsStyle.Independent,
              onExit = { updateAborted(true) },
              onComplete = {}
            )
          )
        }
        else -> error("Unexpected KeyboxData state $accountData")
      }
    }
  }

  lateinit var app: AppTester
  lateinit var recoveryStateMachine: TestingStateMachine

  suspend fun TestScope.launchAndPrepareApp() {
    app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()
    app.fakeNfcCommands.wipeDevice()
    recoveryStateMachine =
      TestingStateMachine(
        app.accountDataStateMachine,
        app.lostHardwareRecoveryUiStateMachine,
        app.keyboxDao,
        app.recoverySyncer
      )
  }

  suspend fun relaunchApp() {
    app = app.relaunchApp()
    recoveryStateMachine = TestingStateMachine(
      app.accountDataStateMachine,
      app.lostHardwareRecoveryUiStateMachine,
      app.keyboxDao,
      app.recoverySyncer
    )
  }

  test("lost hardware recovery - happy path") {
    launchAndPrepareApp()
    val keybox = app.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)
    recoveryStateMachine.test(
      props = props,
      testTimeout = 60.seconds,
      turbineTimeout = 30.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
        .clickPrimaryButton()
      awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test(
    "recovery lost hardware - force exiting before cloud backup takes you back to icloud backup"
  ) {
    launchAndPrepareApp()
    val keybox = app.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)

    recoveryStateMachine.test(
      props = props,
      testTimeout = 20.seconds,
      turbineTimeout = 5.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
    }

    relaunchApp()

    recoveryStateMachine.test(
      props = props,
      testTimeout = 20.seconds,
      turbineTimeout = 5.seconds
    ) {
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
        .clickPrimaryButton()
      awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("recovery lost hardware - force exiting in the middle of initiation") {
    launchAndPrepareApp()
    val keybox = app.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)

    recoveryStateMachine.test(
      props = props,
      testTimeout = 20.seconds,
      turbineTimeout = 5.seconds
    ) {
      awaitUntilBody<FormBodyModel>(RECOVERY_NOT_STARTED)
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY)
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

    relaunchApp()

    recoveryStateMachine.test(
      props = props,
      testTimeout = 20.seconds,
      turbineTimeout = 5.seconds
    ) {
      awaitUntilBody<FormBodyModel>(RECOVERY_NOT_STARTED)
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY)
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_PENDING)
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
        .clickPrimaryButton()
      awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test(
    "recovery lost hardware - force exiting after spend key gen and before ddk backup takes you back to DDK Backup"
  ) {
    launchAndPrepareApp()

    val keybox = app.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)
    app.apply {
      recoveryStateMachine.test(
        props = props,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        startRecoveryAndAdvanceToDelayNotify(app)

        awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_HW_DELAY_NOTIFY_DDK_UPLOAD
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        cancelAndIgnoreRemainingEvents()
      }

      relaunchApp()

      recoveryStateMachine.test(
        props = props,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_HW_DELAY_NOTIFY_DDK_UPLOAD
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
          .signInSuccess(CloudStoreAccount1Fake)
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test(
    "recovery lost hardware - force exiting after ddk tap and before ddk backup takes you back to DDK Backup"
  ) {
    launchAndPrepareApp()

    val keybox = app.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)
    app.apply {
      recoveryStateMachine.test(
        props = props,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        startRecoveryAndAdvanceToDelayNotify(app)

        awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
          .clickPrimaryButton()
        awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_HW_DELAY_NOTIFY_DDK_UPLOAD
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        // Initiating NFC
        awaitUntilBody<NfcBodyModel>()
        // Detected NFC
        awaitUntilBody<NfcBodyModel>()
        // Success NFC
        awaitUntilBody<NfcBodyModel>()

        cancelAndIgnoreRemainingEvents()
      }

      relaunchApp()

      recoveryStateMachine.test(
        props = props,
        testTimeout = 20.seconds,
        turbineTimeout = 5.seconds
      ) {
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_HW_DELAY_NOTIFY_DDK_UPLOAD
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
          .clickPrimaryButton()
        awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
          .signInSuccess(CloudStoreAccount1Fake)
        awaitUntilBody<LoadingSuccessBodyModel>(
          LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
        ) {
          state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        }
        awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
          .clickPrimaryButton()
        awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test(
    "recovery lost hardware - force exiting after cloud backup & before sweep takes you back to sweep"
  ) {
    launchAndPrepareApp()
    val keybox = app.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)

    recoveryStateMachine.test(
      props = props,
      testTimeout = 20.seconds,
      turbineTimeout = 5.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_DDK_UPLOAD
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      cancelAndIgnoreRemainingEvents()
    }

    relaunchApp()

    recoveryStateMachine.test(
      props = props,
      testTimeout = 20.seconds,
      turbineTimeout = 5.seconds
    ) {
      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_ZERO_BALANCE)
        .clickPrimaryButton()
      awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("recovery lost hardware - force exiting during D&N wait") {
    launchAndPrepareApp()
    val keybox = app.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)

    recoveryStateMachine.test(
      props = props,
      testTimeout = 20.seconds,
      turbineTimeout = 5.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_DDK_UPLOAD
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("recover lost hardware - sweep real funds") {
    launchAndPrepareApp()
    val account = app.getActiveFullAccount()
    val wallet = app.getActiveWallet()
    app.treasuryWallet.fund(wallet, BitcoinMoney.sats(10_000L))

    val props = Props(account.config, account.keybox.localId)

    recoveryStateMachine.test(
      props = props,
      testTimeout = 30.seconds,
      turbineTimeout = 5.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_DDK_UPLOAD
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)

      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS)
        .clickPrimaryButton()

      awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
    }

    val activeWallet = app.getActiveWallet()
    eventually(
      eventuallyConfig {
        duration = 60.seconds
        interval = 1.seconds
        initialDelay = 1.seconds
      }
    ) {
      activeWallet.sync().shouldBeOk()
      val balance = activeWallet.balance().first()
      balance.total.shouldBeGreaterThan(BitcoinMoney.sats(0))
      // Eventually could iterate to calculate and subtract psbtsGeneratedData.totalFeeAmount)
    }
    app.returnFundsToTreasury()
  }

  test("can Lost App from Cloud recovery then Lost Hardware recovery with funds") {
    app = launchNewApp()
    app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    // Fund wallet with some funds
    app.addSomeFunds()

    // Create new blank app, persist cloud backups
    val newApp = launchNewApp(
      cloudStoreAccountRepository = app.cloudStoreAccountRepository,
      cloudKeyValueStore = app.cloudKeyValueStore,
      hardwareSeed = app.fakeHardwareKeyStore.getSeed(),
      executeWorkers = true
    )

    // Lost App recovery from Cloud
    newApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<FormBodyModel>(CloudEventTrackerScreenId.CLOUD_BACKUP_FOUND)
        .clickPrimaryButton()
      screenDecideIfShouldRotate {
        clickPrimaryButton()
      }
      newApp.waitForFunds()
      awaitUntilBody<MoneyHomeBodyModel>(
        matching = { it.balanceModel.secondaryAmount != "0 sats" }
      )

      cancelAndIgnoreRemainingEvents()
    }

    newApp.fakeNfcCommands.wipeDevice()
    recoveryStateMachine =
      TestingStateMachine(
        newApp.accountDataStateMachine,
        newApp.lostHardwareRecoveryUiStateMachine,
        newApp.keyboxDao,
        newApp.recoverySyncer
      )

    // Complete Lost Hardware Recovery with D&N
    val keybox = newApp.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)

    recoveryStateMachine.test(
      props = props,
      testTimeout = 30.seconds,
      turbineTimeout = 5.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)

      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS)
        .clickPrimaryButton()

      awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)

      newApp.waitForFunds()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("can Lost Hardware recovery then Lost App recovery from Cloud with funds") {
    launchAndPrepareApp()
    // Fund wallet with some funds
    app.addSomeFunds()

    // Complete Lost Hardware Recovery with D&N
    val keybox = app.getActiveFullAccount().keybox
    val props = Props(keybox.config, keybox.localId)

    recoveryStateMachine.test(
      props = props,
      testTimeout = 30.seconds,
      turbineTimeout = 5.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)

      awaitUntilBody<LoadingSuccessBodyModel>(
        LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT)
        .clickPrimaryButton()
      awaitUntilBody<LoadingSuccessBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_BROADCASTING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_SWEEP_SUCCESS)
        .clickPrimaryButton()

      awaitUntilBody<FormBodyModel>(RECOVERY_COMPLETED)
      cancelAndIgnoreRemainingEvents()
    }

    // Create new blank app, persist cloud backups, keep hardware
    val newApp = launchNewApp(
      cloudStoreAccountRepository = app.cloudStoreAccountRepository,
      cloudKeyValueStore = app.cloudKeyValueStore,
      hardwareSeed = app.fakeHardwareKeyStore.getSeed(),
      executeWorkers = true
    )

    // Lost App recovery from Cloud
    newApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<FormBodyModel>(CloudEventTrackerScreenId.CLOUD_BACKUP_FOUND)
        .clickPrimaryButton()
      screenDecideIfShouldRotate {
        clickPrimaryButton()
      }
      newApp.waitForFunds()
      awaitUntilBody<MoneyHomeBodyModel>(
        matching = { it.balanceModel.secondaryAmount != "0 sats" }
      )

      cancelAndIgnoreRemainingEvents()
    }
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.startRecoveryAndAdvanceToDelayNotify(
  app: AppTester,
) {
  awaitUntilBody<FormBodyModel>(RECOVERY_NOT_STARTED)
  awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<FormBodyModel>(LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY)
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_PAIR_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitUntilBody<LoadingSuccessBodyModel>(
    LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY
  )
  awaitUntilBody<FormBodyModel>(
    LOST_HW_DELAY_NOTIFY_PENDING,
    matching = { it.header?.headline == "Replacement in progress..." }
  )
  app.completeRecoveryDelayPeriodOnF8e()
}
