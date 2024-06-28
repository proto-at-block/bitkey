package build.wallet.statemachine.data.keybox

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.recovery.Recovery
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.OnboardConfig
import build.wallet.statemachine.data.account.create.CreateFullAccountDataProps
import build.wallet.statemachine.data.account.create.CreateFullAccountDataStateMachine
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CheckingRecoveryOrOnboarding
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.AttemptingCloudRecoveryLostAppRecoveryDataData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryProps
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class NoActiveAccountDataStateMachineImplTests : FunSpec({
  val lostAppRecoveryDataStateMachine =
    object : LostAppRecoveryDataStateMachine,
      StateMachineMock<LostAppRecoveryProps, LostAppRecoveryData>(
        AttemptingCloudRecoveryLostAppRecoveryDataData(
          cloudBackup = CloudBackupV2WithFullAccountMock,
          rollback = {}
        )
      ) {}
  val createFullAccountDataStateMachine =
    object : CreateFullAccountDataStateMachine,
      StateMachineMock<CreateFullAccountDataProps, CreateFullAccountData>(
        initialModel =
          CreateFullAccountData.CreateKeyboxData.CreatingAppKeysData(
            fullAccountConfig = FullAccountConfigMock,
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
      LoadedTemplateFullAccountConfigData(
        config = FullAccountConfigMock,
        updateConfig = {}
      ),
      existingRecovery = existingRecovery,
      onAccountCreated = accountCreatedCalls::add,
      onboardConfig = OnboardConfig(stepsToSkip = emptySet())
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
    keyboxDao.onboardingKeybox.emit(Ok(KeyboxMock))
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
