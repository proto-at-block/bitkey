package build.wallet.statemachine.recovery.losthardware.initiate

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_HW_RECOVERY_STARTED
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.FailedBuildingKeyCrossData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.FailedInitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.InitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.VerifyingNotificationCommsData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okio.ByteString.Companion.encodeUtf8

class InitiatingLostHardwareRecoveryUiStateMachineImplTests : FunSpec({

  val pairNewHardwareUiStateMachine =
    object : PairNewHardwareUiStateMachine,
      ScreenStateMachineMock<PairNewHardwareProps>(
        id = "pairing new hardware"
      ) {}

  val recoveryNotificationVerificationUiStateMachine =
    object : RecoveryNotificationVerificationUiStateMachine,
      ScreenStateMachineMock<RecoveryNotificationVerificationUiProps>(
        id = "verifying notification comms"
      ) {}

  val proofOfPossessionUiStateMachine =
    object : ProofOfPossessionNfcStateMachine,
      ScreenStateMachineMock<ProofOfPossessionNfcProps>(
        id = "proof of possesion"
      ) {}

  val addHardwareKeysCalls =
    turbines.create<Pair<SealedCsek, HwKeyBundle>>("add hardware keys calls")

  val onExitCalls = turbines.create<Unit>("on exit calls")

  val rollbackCalls = turbines.create<Unit>("rollback calls")

  val retryCalls = turbines.create<Unit>("retry calls")

  val eventTracker = EventTrackerMock(turbines::create)

  val stateMachine =
    InitiatingLostHardwareRecoveryUiStateMachineImpl(
      pairNewHardwareUiStateMachine = pairNewHardwareUiStateMachine,
      eventTracker = eventTracker,
      recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine,
      proofOfPossessionNfcStateMachine = proofOfPossessionUiStateMachine
    )

  val sealedCsekMock = "sealedCsek".encodeUtf8()

  val awaitingProps =
    InitiatingLostHardwareRecoveryProps(
      keyboxConfig = KeyboxConfigMock,
      fullAccountId = FullAccountIdMock,
      screenPresentationStyle = Modal,
      instructionsStyle = InstructionsStyle.Independent,
      initiatingLostHardwareRecoveryData =
        AwaitingNewHardwareData(
          addHardwareKeys = { sealedCsek: SealedCsek, keyBundle: HwKeyBundle ->
            addHardwareKeysCalls += sealedCsek to keyBundle
          }
        ),
      onFoundHardware = {},
      onExit = {
        onExitCalls += Unit
      }
    )

  val initiatingProps =
    awaitingProps.copy(
      initiatingLostHardwareRecoveryData =
        InitiatingRecoveryWithF8eData(
          rollback = {
            rollbackCalls += Unit
          }
        )
    )

  val failedBuildingKeycrossProps =
    awaitingProps.copy(
      initiatingLostHardwareRecoveryData =
        FailedBuildingKeyCrossData(
          retry = {
            retryCalls += Unit
          },
          rollback = {
            rollbackCalls += Unit
          }
        )
    )

  val failedInitiatingF8eProps =
    awaitingProps.copy(
      initiatingLostHardwareRecoveryData =
        FailedInitiatingRecoveryWithF8eData(
          retry = {
            retryCalls += Unit
          },
          rollback = {
            rollbackCalls += Unit
          }
        )
    )

  val verifyingNotificationCommsProps =
    awaitingProps.copy(
      initiatingLostHardwareRecoveryData =
        VerifyingNotificationCommsData(
          data = RecoveryNotificationVerificationData.LoadingNotificationTouchpointData
        )
    )

  test("initiating lost hardware recovery ui -- success through awaiting new hardware") {
    stateMachine.test(
      props = awaitingProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitScreenWithBodyModelMock<PairNewHardwareProps> {
        onSuccess.shouldNotBeNull().invoke(
          PairingTransactionResponse.FingerprintEnrolled(HwKeyBundleMock, sealedCsekMock, "")
        )
      }

      addHardwareKeysCalls.awaitItem().let { (sealedCsek, keyBundle) ->
        sealedCsek.shouldBeEqual(sealedCsekMock)
        keyBundle.shouldBeEqual(HwKeyBundleMock)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_HW_RECOVERY_STARTED))
    }
  }

  test("initiating lost hardware recovery ui -- close instructions") {
    stateMachine.test(
      props = awaitingProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        onBack.shouldNotBeNull()()
      }

      onExitCalls.awaitItem()
    }
  }

  test("initiating lost hardware recovery ui -- back from new device ready question") {
    stateMachine.test(
      props = awaitingProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        onBack.shouldNotBeNull()()
      }

      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
      }
    }
  }

  test("initiating lost hardware recovery ui -- no from new device ready question") {
    stateMachine.test(
      props = awaitingProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        secondaryButton.shouldNotBeNull().onClick()
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        alertModel.shouldNotBeNull()
      }
    }
  }

  test(
    "initiating lost hardware recovery ui -- no from new device ready question and dismiss"
  ) {
    stateMachine.test(
      props = awaitingProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        primaryButton.shouldNotBeNull().onClick()
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        alertModel.shouldBeNull()
        with(body.shouldBeInstanceOf<FormBodyModel>()) {
          secondaryButton.shouldNotBeNull().onClick()
        }
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        alertModel.shouldNotBeNull()
          .onDismiss()
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        bottomSheetModel.shouldBeNull()
      }
    }
  }

  test("initiating lost hardware recovery ui -- success through initiating with f8e") {
    stateMachine.test(
      props = initiatingProps
    ) {
      awaitScreenWithBody<LoadingBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY
          )
      }
    }
  }

  test("initiating lost hardware recovery ui -- rollback while initiating with f8e") {
    stateMachine.test(
      props = initiatingProps
    ) {
      awaitScreenWithBody<LoadingBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY
          )
        onBack.shouldNotBeNull()()
      }
      rollbackCalls.awaitItem()
    }
  }

  test("initiating lost hardware recovery ui -- key cross failure") {
    stateMachine.test(
      props = failedBuildingKeycrossProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR)
      }
    }
  }

  test("initiating lost hardware recovery ui -- key cross failure retry") {
    stateMachine.test(
      props = failedBuildingKeycrossProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR)
        primaryButton.shouldNotBeNull().onClick()
      }
      retryCalls.awaitItem()
    }
  }

  test("initiating lost hardware recovery ui -- key cross failure rollback") {
    stateMachine.test(
      props = failedBuildingKeycrossProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR)
        secondaryButton.shouldNotBeNull().onClick()
      }
      rollbackCalls.awaitItem()
    }
  }

  test("initiating lost hardware recovery ui -- f8e failure") {
    stateMachine.test(
      props = failedInitiatingF8eProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR)
      }
    }
  }

  test("initiating lost hardware recovery ui -- f8e failure retry") {
    stateMachine.test(
      props = failedInitiatingF8eProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR)
        primaryButton.shouldNotBeNull().onClick()
      }
      retryCalls.awaitItem()
    }
  }

  test("initiating lost hardware recovery ui -- f8e failure rollback") {
    stateMachine.test(
      props = failedInitiatingF8eProps
    ) {
      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR)
        secondaryButton.shouldNotBeNull().onClick()
      }
      rollbackCalls.awaitItem()
    }
  }

  test("initiating lost hardware recovery ui -- verifying comms") {
    stateMachine.test(props = verifyingNotificationCommsProps) {
      awaitScreenWithBodyModelMock<RecoveryNotificationVerificationUiProps> {
        recoveryNotificationVerificationData.shouldBe(
          RecoveryNotificationVerificationData.LoadingNotificationTouchpointData
        )
      }
    }
  }
})
