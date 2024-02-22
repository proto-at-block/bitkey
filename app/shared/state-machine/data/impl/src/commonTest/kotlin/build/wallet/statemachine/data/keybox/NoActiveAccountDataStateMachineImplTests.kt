package build.wallet.statemachine.data.keybox

import build.wallet.LoadableValue
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.money.display.CurrencyPreferenceDataMock
import build.wallet.recovery.Recovery
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.create.CreateFullAccountDataProps
import build.wallet.statemachine.data.account.create.CreateFullAccountDataStateMachine
import build.wallet.statemachine.data.account.create.LoadedOnboardConfigDataMock
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CheckingRecoveryOrOnboarding
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.AttemptingCloudRecoveryLostAppRecoveryDataData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryProps
import build.wallet.statemachine.data.recovery.lostapp.cloud.RecoveringKeyboxFromCloudBackupData.AccessingCloudBackupData
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class NoActiveAccountDataStateMachineImplTests : FunSpec({
  val lostAppRecoveryDataStateMachine =
    object : LostAppRecoveryDataStateMachine,
      StateMachineMock<LostAppRecoveryProps, LostAppRecoveryData>(
        AttemptingCloudRecoveryLostAppRecoveryDataData(
          AccessingCloudBackupData(
            onCloudBackupNotAvailable = {},
            onCloudBackupFound = {},
            onImportEmergencyAccessKit = {},
            rollback = {}
          )
        )
      ) {}
  val createFullAccountDataStateMachine =
    object : CreateFullAccountDataStateMachine,
      StateMachineMock<CreateFullAccountDataProps, CreateFullAccountData>(
        initialModel =
          CreateFullAccountData.CreateKeyboxData.CreatingAppKeysData(
            keyboxConfig = KeyboxConfigMock,
            rollback = {}
          )
      ) {}
  val eventTracker = EventTrackerMock(turbines::create)
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val accountCreatedCalls = turbines.create<Account>("account created calls")

  val stateMachine =
    NoActiveAccountDataStateMachineImpl(
      createFullAccountDataStateMachine = createFullAccountDataStateMachine,
      lostAppRecoveryDataStateMachine = lostAppRecoveryDataStateMachine,
      eventTracker = eventTracker,
      keyboxDao = keyboxDao
    )

  beforeTest {
    createFullAccountDataStateMachine.reset()
    lostAppRecoveryDataStateMachine.reset()
    keyboxDao.reset()
  }

  // If this state machine is running, it means that there is no app key.
  suspend fun EventTrackerMock.shouldLogAppKeyMissingEvent() {
    eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_OPEN_KEY_MISSING))
  }

  fun props(existingRecovery: Recovery.StillRecovering? = null) =
    NoActiveAccountDataProps(
      LoadedTemplateKeyboxConfigData(
        config = KeyboxConfigMock,
        updateConfig = {}
      ),
      existingRecovery = existingRecovery,
      currencyPreferenceData = CurrencyPreferenceDataMock,
      onAccountCreated = accountCreatedCalls::add,
      newAccountOnboardConfigData = LoadedOnboardConfigDataMock
    )

  test("no onboarding in progress and no recovery in progress") {
    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<CheckingRecoveryOrOnboarding>()

      eventTracker.shouldLogAppKeyMissingEvent()

      awaitItem().shouldBeTypeOf<GettingStartedData>()
    }
  }

  test("lost app recovery in progress") {
    stateMachine.test(props(existingRecovery = StillRecoveringInitiatedRecoveryMock)) {
      awaitItem().shouldBeTypeOf<AccountData.NoActiveAccountData.RecoveringAccountData>()

      eventTracker.shouldLogAppKeyMissingEvent()
    }
  }

  test("no recovery in progress, onboarding in progress") {
    keyboxDao.onboardingKeybox.emit(Ok(LoadableValue.LoadedValue(KeyboxMock)))
    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<CheckingRecoveryOrOnboarding>()

      eventTracker.shouldLogAppKeyMissingEvent()

      awaitItem().shouldBeTypeOf<AccountData.NoActiveAccountData.CreatingFullAccountData>()
    }
  }

  test("no onboarding or recovery, transition to emergency access kit recovery") {
    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<CheckingRecoveryOrOnboarding>()

      eventTracker.shouldLogAppKeyMissingEvent()

      awaitItem().shouldBeTypeOf<GettingStartedData>()
        .startEmergencyAccessRecovery()

      awaitItem()
        .shouldBeTypeOf<AccountData.NoActiveAccountData.RecoveringAccountWithEmergencyAccessKit>()
    }
  }
})
