package build.wallet.statemachine.account.create.full.hardware

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_HW_FINGERPRINT_COMPLETE
import build.wallet.analytics.v1.Action.ACTION_HW_ONBOARDING_FINGERPRINT
import build.wallet.analytics.v1.Action.ACTION_HW_ONBOARDING_OPEN
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.transaction.PairingTransactionProviderFake
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrollmentStarted
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintNotEnrolled
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString.Companion.encodeUtf8

class PairNewHardwareUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)

  val pairingTransactionProvider = PairingTransactionProviderFake()

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  val helpCenterUiStateMachine =
    object : HelpCenterUiStateMachine,
      ScreenStateMachineMock<HelpCenterUiProps>("help-center") {}

  val stateMachine = PairNewHardwareUiStateMachineImpl(
    eventTracker = eventTracker,
    pairingTransactionProvider = pairingTransactionProvider,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    helpCenterUiStateMachine = helpCenterUiStateMachine
  )

  val onSuccessCalls = turbines.create<FingerprintEnrolled>("on success calls")

  val onExitCalls = turbines.create<Unit>("on exit calls")

  val sealedCsekMock = "sealedCsek".encodeUtf8()

  val fingerprintEnrolled = FingerprintEnrolled(
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    keyBundle = HwKeyBundleMock,
    sealedCsek = sealedCsekMock,
    serial = "123"
  )

  val props = PairNewHardwareProps(
    request = PairNewHardwareProps.Request.Ready(
      appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
      fullAccountConfig = FullAccountConfigMock,
      onSuccess = onSuccessCalls::add
    ),
    screenPresentationStyle = Modal,
    onExit = {
      onExitCalls += Unit
    },
    eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
  )

  test("pairing new wallet ui -- success") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted)
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))

      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }

  test("pairing new wallet ui -- fingerprint already enrolled") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))

      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }

  test("pairing new wallet ui -- fingerprint not enrolled overlay closed") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted)
      }

      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        with(body.shouldBeInstanceOf<PairNewHardwareBodyModel>()) {
          eventTrackerScreenInfo.shouldNotBeNull()
            .eventTrackerScreenId
            .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
          primaryButton.onClick()
        }
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintNotEnrolled)
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldNotBeNull()
          .onClosed()
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldBeNull()
      }
    }
  }

  test("pairing new wallet ui -- fingerprint not enrolled button clicked") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted)
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldBeNull()
        body.shouldBeInstanceOf<PairNewHardwareBodyModel>()
          .primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintNotEnrolled)
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeTypeOf<FormBodyModel>()
          .clickPrimaryButton()
      }

      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
      }
    }
  }

  test("pairing new wallet ui -- fingerprint enrollment restarted overlay closed") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted)
      }

      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        with(body.shouldBeInstanceOf<PairNewHardwareBodyModel>()) {
          eventTrackerScreenInfo.shouldNotBeNull()
            .eventTrackerScreenId
            .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
          primaryButton.onClick()
        }
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted)
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldNotBeNull()
          .onClosed()
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldBeNull()
      }
    }
  }

  test("pairing new wallet ui -- fingerprint enrollment restarted button clicked") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted)
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldBeNull()
        body.shouldBeInstanceOf<PairNewHardwareBodyModel>()
          .primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted)
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeTypeOf<FormBodyModel>()
          .clickPrimaryButton()
      }

      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
      }
    }
  }

  test("pairing new wallet ui -- back on save fingerprint instructions") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted)
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        onBack.shouldNotBeNull().invoke()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
      }
    }
  }

  test("pairing new wallet ui -- cancel start fingerprint enrollment") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Boolean>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onCancel()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
      }
    }
  }

  test("pairing new wallet ui -- back from showing fingerprint enrollment instructions") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        onBack.shouldNotBeNull().invoke()
      }

      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
      }
    }
  }

  test("pairing new wallet ui -- back from showing activation instructions") {
    stateMachine.test(props) {
      awaitScreenWithBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        onBack.shouldNotBeNull().invoke()
      }

      onExitCalls.awaitItem()
    }
  }
})
