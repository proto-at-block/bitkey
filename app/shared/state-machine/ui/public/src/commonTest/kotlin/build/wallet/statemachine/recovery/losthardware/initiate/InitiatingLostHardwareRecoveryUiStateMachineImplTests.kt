package build.wallet.statemachine.recovery.losthardware.initiate

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_HW_RECOVERY_STARTED
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.FailedInitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.InitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.VerifyingNotificationCommsData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.ui.model.alert.ButtonAlertModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
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

  val awaitingProps = InitiatingLostHardwareRecoveryProps(
    account = FullAccountMock,
    screenPresentationStyle = Modal,
    instructionsStyle = InstructionsStyle.Independent,
    initiatingLostHardwareRecoveryData = AwaitingNewHardwareData(
      newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
      addHardwareKeys = {
          sealedCsek: SealedCsek,
          keyBundle: HwKeyBundle,
          _: AppGlobalAuthKeyHwSignature,
        ->
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

  val failedInitiatingF8eProps =
    awaitingProps.copy(
      initiatingLostHardwareRecoveryData =
        FailedInitiatingRecoveryWithF8eData(
          cause = Exception(),
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
        clickPrimaryButton()
      }

      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        clickPrimaryButton()
      }

      awaitScreenWithBodyModelMock<PairNewHardwareProps> {
        request
          .shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
          .onSuccess(
            PairingTransactionResponse.FingerprintEnrolled(
              appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
              keyBundle = HwKeyBundleMock,
              sealedCsek = sealedCsekMock,
              serial = ""
            )
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
        clickPrimaryButton()
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
        clickPrimaryButton()
      }

      awaitScreenWithBody<FormBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        clickSecondaryButton()
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
        clickPrimaryButton()
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        alertModel.shouldBeNull()
        with(body.shouldBeInstanceOf<FormBodyModel>()) {
          clickSecondaryButton()
        }
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        alertModel.shouldBeTypeOf<ButtonAlertModel>()
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
      awaitScreenWithBody<LoadingSuccessBodyModel> {
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
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY
          )
        onBack.shouldNotBeNull()()
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
        clickPrimaryButton()
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
        clickSecondaryButton()
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
