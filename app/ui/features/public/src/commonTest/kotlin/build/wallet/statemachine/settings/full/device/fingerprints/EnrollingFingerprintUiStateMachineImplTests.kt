package build.wallet.statemachine.settings.full.device.fingerprints

import app.cash.turbine.plusAssign
import bitkey.privilegedactions.FingerprintResetF8eClientFake
import bitkey.privilegedactions.FingerprintResetServiceFake
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FingerprintHandle
import build.wallet.grants.Grant
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetEnrollmentFailureBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetGrantProvisionResult
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EnrollingFingerprintUiStateMachineImplTests : FunSpec({
  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
      "nfc fingerprints"
    ) {}
  val clock = ClockFake()
  val stateMachine = EnrollingFingerprintUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    fingerprintNfcCommands = FingerprintNfcCommandsImpl(),
    fingerprintResetGrantNfcHandler = FingerprintResetGrantNfcHandler(
      fingerprintResetService = FingerprintResetServiceFake(
        FingerprintResetF8eClientFake(
          clock = clock
        ),
        clock = clock
      )
    )
  )

  val onCancelCalls = turbines.create<Unit>("on cancel calls")
  val onSuccessCalls = turbines.create<EnrolledFingerprints>("on success calls")

  val testGrant = Grant(
    version = 1,
    serializedRequest = byteArrayOf(1, 2, 3, 4),
    signature = byteArrayOf(5, 6, 7, 8)
  )

  val fingerprintHandle = FingerprintHandle(index = 2, label = "Right Thumb")
  val props = EnrollingFingerprintProps(
    onCancel = { onCancelCalls += Unit },
    onSuccess = { onSuccessCalls += it },
    fingerprintHandle = fingerprintHandle,
    enrolledFingerprints = EnrolledFingerprints(
      fingerprintHandles = immutableListOf(fingerprintHandle)
    ),
    context = EnrollmentContext.AddingFingerprint
  )

  val nfcCommandsMock = NfcCommandsMock(turbines::create)

  test("start enrollment then cancel") {
    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onCancel()
      }

      nfcCommandsMock.awaitEnrollmentNfcCommands()

      onCancelCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("cancelling confirmation goes back to instructions") {
    stateMachine.test(props) {
      startEnrollmentAndTapSaveFingerprint(nfcCommandsMock)
      awaitBodyMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onCancel()
      }

      // Check that the most recent fingerprints are retrieved
      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()

      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Add a new fingerprint")
      }
    }
  }

  test("backing out of instructions invokes props.onCancel") {
    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<Boolean>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(true)
      }

      nfcCommandsMock.awaitEnrollmentNfcCommands()

      // See the fingerprint instructions and tap the "Save fingerprint" button, which prompts to
      // tap again for enrollment status
      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Add a new fingerprint")
        onBack.shouldNotBeNull().invoke()
      }

      onCancelCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("successful enrollment returns latest fingerprints") {
    stateMachine.test(props) {
      startEnrollmentAndTapSaveFingerprint(nfcCommandsMock)

      val enrolledFingerprints = EnrolledFingerprints(
        fingerprintHandles = immutableListOf(
          FingerprintHandle(index = 0, label = "Left Thumb"),
          FingerprintHandle(index = 2, label = "Right Thumb")
        )
      )
      awaitBodyMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        hardwareVerification.shouldBeInstanceOf<Required>()
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(EnrollmentStatusResult.Complete(enrolledFingerprints))
      }

      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()
      nfcCommandsMock.deleteFingerprintCalls.expectNoEvents()
      onSuccessCalls.awaitItem().shouldBe(enrolledFingerprints)
    }
  }

  test("enrollment incomplete shows an error") {
    stateMachine.test(props) {
      startEnrollmentAndTapSaveFingerprint(nfcCommandsMock)

      awaitBodyMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(EnrollmentStatusResult.Incomplete)
      }

      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()

      // See the error overlay
      awaitSheet<FormBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Incomplete Fingerprint Scan")

        // Close error overlay
        clickPrimaryButton()
      }

      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Add a new fingerprint")
      }
    }
  }

  test("FingerprintReset context skips initial NFC session and goes directly to instructions") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      // Should go directly to fingerprint instructions, skipping the initial NFC session
      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
      }

      // No enrollment NFC commands should be called since we skip the initial NFC session
      nfcCommandsMock.cancelFingerprintEnrollmentCalls.expectNoEvents()
      nfcCommandsMock.getEnrolledFingerprintsCalls.expectNoEvents()
      nfcCommandsMock.startFingerprintEnrollmentCalls.expectNoEvents()
    }
  }

  test("FingerprintReset context performs fingerprint deletion during confirmation") {
    val fingerprintResetProps = props.copy(
      context = EnrollmentContext.FingerprintReset(grant = testGrant),
      fingerprintHandle = FingerprintHandle(index = 0, label = "Right Thumb")
    )

    nfcCommandsMock.setEnrolledFingerprints(
      enrolledFingerprints = EnrolledFingerprints(
        fingerprintHandles = immutableListOf(
          fingerprintHandle,
          FingerprintHandle(index = 1, label = "Left Thumb")
        )
      )
    )

    nfcCommandsMock.setEnrollmentStatus(FingerprintEnrollmentStatus.COMPLETE)

    stateMachine.test(fingerprintResetProps) {
      // Start directly at instructions
      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
        primaryButton.apply {
          text.shouldBe("Save fingerprint")
          onClick()
        }
      }

      // Should proceed to confirmation which includes deletion
      awaitBodyMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(EnrollmentStatusResult.Complete(props.enrolledFingerprints))
      }

      // Should perform deletion (getEnrolledFingerprints + 2 deleteFingerprint calls)
      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()
      nfcCommandsMock.deleteFingerprintCalls.awaitItem()
      nfcCommandsMock.deleteFingerprintCalls.awaitItem()

      onSuccessCalls.awaitItem().shouldBe(props.enrolledFingerprints)
    }
  }

  test("FingerprintReset context shows troubleshooting button") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
        secondaryButton.shouldNotBeNull().apply {
          text.shouldBe("Having trouble?")
        }
      }
    }
  }

  test("AddingFingerprint context does not show troubleshooting button") {
    val addingFingerprintProps = props.copy(context = EnrollmentContext.AddingFingerprint)

    stateMachine.test(addingFingerprintProps) {
      awaitBodyMock<NfcSessionUIStateMachineProps<Boolean>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(true)
      }

      nfcCommandsMock.awaitEnrollmentNfcCommands()

      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Add a new fingerprint")
        secondaryButton.shouldBeNull()
      }

      nfcCommandsMock.deleteFingerprintCalls.awaitItem()
    }
  }

  test("troubleshooting button shows troubleshooting sheet") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      awaitBody<PairNewHardwareBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FormBodyModel> {
        header.shouldNotBeNull().apply {
          headline.shouldBe("Wake your Bitkey device")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "To begin troubleshooting, press the fingerprint sensor until you see a light."
          )
        }
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Continue")
        }
      }
    }
  }

  test("troubleshooting sheet can be dismissed") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      awaitBody<PairNewHardwareBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull().onClosed()
      }

      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("troubleshooting sheet Continue button starts grant provision NFC session") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      awaitBody<PairNewHardwareBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>> {
        eventTrackerContext.shouldBe(NfcEventTrackerScreenIdContext.ENROLLING_NEW_FINGERPRINT)
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(FingerprintResetGrantProvisionResult.ProvideGrantSuccess)
      }

      awaitBody<FingerprintResetEnrollmentFailureBodyModel> {
        header.shouldNotBeNull().apply {
          headline.shouldBe("Let's try this again")
          sublineModel.shouldNotBeNull().string.shouldBe("Your fingerprint wasn't saved, but your device is ready to try again.")
        }
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Try again")
        }
      }

      nfcCommandsMock.provideGrantCalls.awaitItem()
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem()
    }
  }

  test("successful grant provision through troubleshooting leads to 'try again' flow") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      navigateToTroubleshootingNfcSession()

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(FingerprintResetGrantProvisionResult.ProvideGrantSuccess)
      }

      awaitBody<FingerprintResetEnrollmentFailureBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Try again")
          onClick()
        }
      }

      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
      }

      nfcCommandsMock.provideGrantCalls.awaitItem()
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem()
    }
  }

  test("grant provision failure shows error screen with retry option") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      navigateToTroubleshootingNfcSession()

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(FingerprintResetGrantProvisionResult.ProvideGrantFailed)
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().apply {
          headline.shouldBe("Grant Delivery Failed")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "We couldn't deliver the authorization grant to your hardware. Please try again."
          )
        }
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Retry")
        }
        secondaryButton.shouldNotBeNull().apply {
          text.shouldBe("Cancel")
        }
      }

      nfcCommandsMock.provideGrantCalls.awaitItem()
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem()
    }
  }

  test("grant already delivered during troubleshooting completes fingerprint reset") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))
    val expectedFingerprints = EnrolledFingerprints(
      fingerprintHandles = immutableListOf(FingerprintHandle(index = 0, label = "New Fingerprint"))
    )

    stateMachine.test(fingerprintResetProps) {
      navigateToTroubleshootingNfcSession()

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(FingerprintResetGrantProvisionResult.FingerprintResetComplete(expectedFingerprints))
      }

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Completing fingerprint enrollment...")
      }

      onSuccessCalls.awaitItem().shouldBe(expectedFingerprints)
      nfcCommandsMock.provideGrantCalls.awaitItem()
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem()
    }
  }

  test("troubleshooting NFC session cancellation returns to instructions") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      navigateToTroubleshootingNfcSession()

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onCancel()
      }

      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
      }

      nfcCommandsMock.provideGrantCalls.awaitItem()
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem()
    }
  }

  test("error screen retry button returns to instructions") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      navigateToTroubleshootingNfcSession()

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(FingerprintResetGrantProvisionResult.ProvideGrantFailed)
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
      }

      nfcCommandsMock.provideGrantCalls.awaitItem()
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem()
    }
  }

  test("error screen cancel button returns to instructions") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))

    stateMachine.test(fingerprintResetProps) {
      navigateToTroubleshootingNfcSession()

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(FingerprintResetGrantProvisionResult.ProvideGrantFailed)
      }

      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
      }

      nfcCommandsMock.provideGrantCalls.awaitItem()
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem()
    }
  }

  test("complete troubleshooting flow: failure -> retry -> success") {
    val fingerprintResetProps =
      props.copy(context = EnrollmentContext.FingerprintReset(grant = testGrant))
    val expectedFingerprints = EnrolledFingerprints(
      fingerprintHandles = immutableListOf(
        FingerprintHandle(
          index = 0,
          label = "Reset Fingerprint"
        )
      )
    )

    stateMachine.test(fingerprintResetProps) {
      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
        primaryButton.apply {
          text.shouldBe("Save fingerprint")
          onClick()
        }
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(EnrollmentStatusResult.Incomplete)
      }

      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()

      awaitSheet<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Incomplete Fingerprint Scan")
        clickPrimaryButton()
      }

      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitSheet<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Wake your Bitkey device")
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(FingerprintResetGrantProvisionResult.ProvideGrantSuccess)
      }

      awaitBody<FingerprintResetEnrollmentFailureBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Try again")
          onClick()
        }
      }

      awaitBody<PairNewHardwareBodyModel> {
        header.headline.shouldBe("Set up your fingerprint")
        primaryButton.apply {
          text.shouldBe("Save fingerprint")
          onClick()
        }
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(EnrollmentStatusResult.Complete(expectedFingerprints))
      }

      nfcCommandsMock.getEnrolledFingerprintsCalls.awaitItem()
      nfcCommandsMock.deleteFingerprintCalls.awaitItem()
      nfcCommandsMock.deleteFingerprintCalls.awaitItem()
      nfcCommandsMock.provideGrantCalls.awaitItem()
      nfcCommandsMock.startFingerprintEnrollmentCalls.awaitItem()

      onSuccessCalls.awaitItem().shouldBe(expectedFingerprints)
    }
  }

  test("FingerprintReset context does not require hardware pairing") {
    val fingerprintResetProps = props.copy(
      context = EnrollmentContext.FingerprintReset(grant = testGrant),
      fingerprintHandle = FingerprintHandle(index = 0, label = "Right Thumb")
    )

    stateMachine.test(fingerprintResetProps) {
      awaitBody<PairNewHardwareBodyModel> {
        primaryButton.onClick()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<EnrollmentStatusResult>> {
        hardwareVerification.shouldBe(NotRequired)
      }
    }
  }
})

private suspend fun StateMachineTester<EnrollingFingerprintProps, ScreenModel>.startEnrollmentAndTapSaveFingerprint(
  nfcCommandsMock: NfcCommandsMock,
) = apply {
  // Start the fingerprint enrollment
  awaitBodyMock<NfcSessionUIStateMachineProps<Boolean>> {
    session(NfcSessionFake(), nfcCommandsMock)
    onSuccess(true)
  }

  nfcCommandsMock.awaitEnrollmentNfcCommands()

  // See the fingerprint instructions and tap the "Save fingerprint" button, which prompts to
  // tap again for enrollment status
  awaitBody<PairNewHardwareBodyModel> {
    header.headline.shouldBe("Add a new fingerprint")
    primaryButton.apply {
      text.shouldBe("Save fingerprint")
      onClick.invoke()
    }
  }
}

private suspend fun StateMachineTester<EnrollingFingerprintProps, ScreenModel>.navigateToTroubleshootingNfcSession() {
  awaitBody<PairNewHardwareBodyModel> {
    secondaryButton.shouldNotBeNull().onClick()
  }

  awaitSheet<FormBodyModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
}

private suspend fun NfcCommandsMock.awaitEnrollmentNfcCommands() =
  apply {
    // Confirm that any in-progress enrollment is canceled when starting a new enrollment
    cancelFingerprintEnrollmentCalls.awaitItem().shouldBe(Unit)
    // Check that the most recent fingerprints are retrieved
    getEnrolledFingerprintsCalls.awaitItem()
    // Confirm a new enrollment is started
    startFingerprintEnrollmentCalls.awaitItem()
  }
