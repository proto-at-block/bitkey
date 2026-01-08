package build.wallet.statemachine.data.keybox

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_KEY_MISSING
import build.wallet.cloud.backup.*
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.recovery.StillRecoveringInitiatedRecoveryMock
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.AttemptingCloudRecoveryLostAppRecoveryDataData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryProps
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class NoActiveAccountDataStateMachineImplTests : FunSpec({

  context("parameterized tests for all backup versions") {
    AllFullAccountBackupMocks.forEach { backup ->
      val backupVersion = when (backup) {
        is CloudBackupV2 -> "v2"
        is CloudBackupV3 -> "v3"
        else -> "unknown"
      }

      context("backup $backupVersion") {
        val lostAppRecoveryDataStateMachine =
          object : LostAppRecoveryDataStateMachine,
            StateMachineMock<LostAppRecoveryProps, LostAppRecoveryData>(
              AttemptingCloudRecoveryLostAppRecoveryDataData(
                cloudBackups = listOf(backup as CloudBackup),
                rollback = {},
                onRecoverAppKey = {},
                goToLiteAccountCreation = {}
              )
            ) {}
        val eventTracker = EventTrackerMock { name -> turbines.create("$backupVersion-$name") }
        val keyboxDao =
          KeyboxDaoMock(turbine = { name -> turbines.create("$backupVersion-$name") }, null, null)
        val recoveryStatusService =
          RecoveryStatusServiceMock { name -> turbines.create("$backupVersion-$name") }

        val stateMachine =
          NoActiveAccountDataStateMachineImpl(
            lostAppRecoveryDataStateMachine = lostAppRecoveryDataStateMachine,
            eventTracker = eventTracker,
            recoveryStatusService = recoveryStatusService
          )

        beforeTest {
          lostAppRecoveryDataStateMachine.reset()
          recoveryStatusService.reset()
          keyboxDao.reset()
          Router.reset()
        }

        // If this state machine is running, it means that there is no app key.
        suspend fun EventTrackerMock.shouldLogAppKeyMissingEvent() {
          eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_OPEN_KEY_MISSING))
        }

        fun props() =
          NoActiveAccountDataProps(
            goToLiteAccountCreation = {}
          )

        test("no recovery in progress") {
          stateMachine.test(props()) {
            eventTracker.shouldLogAppKeyMissingEvent()

            awaitItem().shouldBeTypeOf<NoActiveAccountData.GettingStartedData>()
          }
        }

        test("lost app recovery in progress") {
          recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock

          stateMachine.test(props()) {
            awaitItem().shouldBeTypeOf<NoActiveAccountData.RecoveringAccountData>()

            eventTracker.shouldLogAppKeyMissingEvent()
          }
        }

        test("no onboarding or recovery, transition to Emergency Exit Kit recovery") {
          stateMachine.test(props()) {
            eventTracker.shouldLogAppKeyMissingEvent()

            awaitItem().shouldBeTypeOf<NoActiveAccountData.GettingStartedData>()
              .startEmergencyExitRecovery()

            awaitItem()
              .shouldBeTypeOf<NoActiveAccountData.RecoveringAccountWithEmergencyExitKit>()
          }
        }

        test("Beneficiary invite route is handled") {
          Router.route = Route.BeneficiaryInvite("inviteCode")

          stateMachine.test(props()) {
            awaitItem().shouldBeTypeOf<NoActiveAccountData.GettingStartedData>()

            awaitItem().shouldBeTypeOf<NoActiveAccountData.CheckingCloudBackupData>()
              .inviteCode
              .shouldBe("inviteCode")
          }

          eventTracker.shouldLogAppKeyMissingEvent()
        }
      }
    }
  }
})
