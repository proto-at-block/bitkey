package build.wallet.statemachine.recovery.losthardware.initiate

import app.cash.turbine.plusAssign
import bitkey.recovery.InitiateDelayNotifyRecoveryError
import bitkey.recovery.InitiateDelayNotifyRecoveryError.CommsVerificationRequiredError
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_HW_RECOVERY_STARTED
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.recovery.LostHardwareRecoveryServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionBodyModel
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.time.MinimumLoadingDuration
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.milliseconds

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

  val onExitCalls = turbines.create<Unit>("on exit calls")

  val onFoundHardwareCalls = turbines.create<Unit>("on found hardware calls")

  val eventTracker = EventTrackerMock(turbines::create)

  val lostHardwareRecoveryService = LostHardwareRecoveryServiceFake()
  val stateMachine = InitiatingLostHardwareRecoveryUiStateMachineImpl(
    pairNewHardwareUiStateMachine = pairNewHardwareUiStateMachine,
    eventTracker = eventTracker,
    recoveryNotificationVerificationUiStateMachine = recoveryNotificationVerificationUiStateMachine,
    proofOfPossessionNfcStateMachine = proofOfPossessionUiStateMachine,
    lostHardwareRecoveryService = lostHardwareRecoveryService,
    minimumLoadingDuration = MinimumLoadingDuration(0.milliseconds)
  )

  val props = InitiatingLostHardwareRecoveryProps(
    account = FullAccountMock,
    screenPresentationStyle = Modal,
    instructionsStyle = InstructionsStyle.Independent,
    onFoundHardware = {
      onFoundHardwareCalls += Unit
    },
    onExit = {
      onExitCalls += Unit
    }
  )

  test("initiating lost hardware recovery - success") {
    stateMachine.test(
      props = props
    ) {
      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        onContinue()
      }

      awaitBody<NewDeviceReadyQuestionBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        primaryAction.shouldNotBeNull().onClick()
      }

      awaitBodyMock<PairNewHardwareProps> {
        request
          .shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
          .onSuccess(
            PairingTransactionResponse.FingerprintEnrolled(
              appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
              keyBundle = HwKeyBundleMock,
              sealedCsek = SealedCsekFake,
              sealedSsek = SealedSsekFake,
              serial = ""
            )
          )
      }

      // initiating recovery
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_HW_RECOVERY_STARTED))
    }
  }

  test("initiating lost hardware recovery ui - close instructions") {
    stateMachine.test(
      props = props
    ) {
      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        onClose()
      }

      onExitCalls.awaitItem()
    }
  }

  test("initiating lost hardware recovery ui -- back from new device ready question") {
    stateMachine.test(
      props = props
    ) {
      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        onContinue()
      }

      awaitBody<NewDeviceReadyQuestionBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        onBack()
      }

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
      }
    }
  }

  test(
    "initiating lost hardware recovery ui -- no from new device ready question and dismiss"
  ) {
    stateMachine.test(
      props = props
    ) {
      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        onContinue()
      }

      awaitBody<NewDeviceReadyQuestionBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        secondaryAction.shouldNotBeNull().onClick()
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

      awaitBody<NewDeviceReadyQuestionBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
      }
    }
  }

  test("initiating lost hardware recovery ui -- rollback while initiating with f8e") {
    stateMachine.test(props = props) {
      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        onContinue()
      }

      awaitBody<NewDeviceReadyQuestionBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        primaryAction.shouldNotBeNull().onClick()
      }

      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
          .onSuccess(
            PairingTransactionResponse.FingerprintEnrolled(
              appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
              keyBundle = HwKeyBundleMock,
              sealedCsek = SealedCsekFake,
              sealedSsek = SealedSsekFake,
              serial = ""
            )
          )
      }
      // initiating recovery
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)

        onBack.shouldNotBeNull().invoke()
      }

      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_HW_RECOVERY_STARTED))
    }
  }

  test("initiating lost hardware recovery ui -- f8e failure") {
    lostHardwareRecoveryService.initiateResult = Err(InitiateDelayNotifyRecoveryError.OtherError(Error()))
    stateMachine.test(props = props) {
      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        onContinue()
      }

      awaitBody<NewDeviceReadyQuestionBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        primaryAction.shouldNotBeNull().onClick()
      }

      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
          .onSuccess(
            PairingTransactionResponse.FingerprintEnrolled(
              appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
              keyBundle = HwKeyBundleMock,
              sealedCsek = SealedCsekFake,
              sealedSsek = SealedSsekFake,
              serial = ""
            )
          )
      }

      // initiating recovery
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
      }

      awaitBody<FormBodyModel> {
        id.shouldNotBeNull().shouldBeEqual(
          HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR
        )
        // Update service to succeed on retry
        lostHardwareRecoveryService.initiateResult = Ok(Unit)
        clickPrimaryButton()
      }

      // initiating recovery
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_HW_RECOVERY_STARTED))
    }
  }

  test("initiating lost hardware recovery ui -- f8e failure rollback") {
    lostHardwareRecoveryService.initiateResult = Err(InitiateDelayNotifyRecoveryError.OtherError(Error()))
    stateMachine.test(props = props) {
      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        onContinue()
      }

      awaitBody<NewDeviceReadyQuestionBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        primaryAction.shouldNotBeNull().onClick()
      }

      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
          .onSuccess(
            PairingTransactionResponse.FingerprintEnrolled(
              appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
              keyBundle = HwKeyBundleMock,
              sealedCsek = SealedCsekFake,
              sealedSsek = SealedSsekFake,
              serial = ""
            )
          )
      }

      // initiating recovery
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
      }

      awaitBody<FormBodyModel> {
        id.shouldNotBeNull().shouldBeEqual(
          HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_ERROR
        )
        clickSecondaryButton()
      }

      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_HW_RECOVERY_STARTED))
    }
  }

  test("initiating lost hardware recovery ui -- verifying comms") {
    lostHardwareRecoveryService.initiateResult = Err(CommsVerificationRequiredError(Error()))
    stateMachine.test(props = props) {
      // generating new app keys
      awaitBody<LoadingSuccessBodyModel>()

      awaitBody<HardwareReplacementInstructionsModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
          )
        onContinue()
      }

      awaitBody<NewDeviceReadyQuestionBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(
            HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
          )
        primaryAction.shouldNotBeNull().onClick()
      }

      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
          .onSuccess(
            PairingTransactionResponse.FingerprintEnrolled(
              appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
              keyBundle = HwKeyBundleMock,
              sealedCsek = SealedCsekFake,
              sealedSsek = SealedSsekFake,
              serial = ""
            )
          )
      }

      // initiating recovery
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldNotBeNull()
          .shouldBeEqual(HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
      }

      awaitBodyMock<RecoveryNotificationVerificationUiProps> {
        fullAccountId.shouldBe(FullAccountIdMock)
        localLostFactor.shouldBe(Hardware)
        segment.shouldBe(RecoverySegment.DelayAndNotify.LostHardware.Initiation)
        actionDescription.shouldBe("Error verifying notification comms for contested recovery")
        hwFactorProofOfPossession.shouldBeNull()
        onRollback.shouldNotBeNull()
        onComplete.shouldNotBeNull()
      }
    }

    eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_HW_RECOVERY_STARTED))
  }
})
