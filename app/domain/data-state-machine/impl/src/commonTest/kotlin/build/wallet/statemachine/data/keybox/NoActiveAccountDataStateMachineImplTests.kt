package build.wallet.statemachine.data.keybox

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.recovery.Recovery
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CheckingCloudBackupData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.AttemptingCloudRecoveryLostAppRecoveryDataData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryProps
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class NoActiveAccountDataStateMachineImplTests : FunSpec({
  val lostAppRecoveryDataStateMachine =
    object : LostAppRecoveryDataStateMachine,
      StateMachineMock<LostAppRecoveryProps, LostAppRecoveryData>(
        AttemptingCloudRecoveryLostAppRecoveryDataData(
          cloudBackup = CloudBackupV2WithFullAccountMock,
          rollback = {},
          onRecoverAppKey = {},
          goToLiteAccountCreation = {}
        )
      ) {}
  val eventTracker = EventTrackerMock(turbines::create)
  val keyboxDao = KeyboxDaoMock(turbines::create)

  val stateMachine =
    NoActiveAccountDataStateMachineImpl(
      lostAppRecoveryDataStateMachine = lostAppRecoveryDataStateMachine,
      eventTracker = eventTracker
    )

  beforeTest {
    lostAppRecoveryDataStateMachine.reset()
    keyboxDao.reset()
    Router.reset()
  }

  // If this state machine is running, it means that there is no app key.
  suspend fun EventTrackerMock.shouldLogAppKeyMissingEvent() {
    eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_OPEN_KEY_MISSING))
  }

  fun props(existingRecovery: Recovery.StillRecovering? = null) =
    NoActiveAccountDataProps(
      existingRecovery = existingRecovery,
      goToLiteAccountCreation = {}
    )

  test("no recovery in progress") {
    stateMachine.test(props()) {
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

  test("no onboarding or recovery, transition to Emergency Exit Kit recovery") {
    stateMachine.test(props()) {
      eventTracker.shouldLogAppKeyMissingEvent()

      awaitItem().shouldBeTypeOf<GettingStartedData>()
        .startEmergencyExitRecovery()

      awaitItem()
        .shouldBeTypeOf<AccountData.NoActiveAccountData.RecoveringAccountWithEmergencyExitKit>()
    }
  }

  test("Beneficiary invite route is handled") {
    Router.route = Route.BeneficiaryInvite("inviteCode")

    stateMachine.test(props()) {
      awaitItem().shouldBeTypeOf<GettingStartedData>()

      awaitItem().shouldBeTypeOf<CheckingCloudBackupData>()
        .inviteCode
        .shouldBe("inviteCode")
    }

    eventTracker.shouldLogAppKeyMissingEvent()
  }
})
