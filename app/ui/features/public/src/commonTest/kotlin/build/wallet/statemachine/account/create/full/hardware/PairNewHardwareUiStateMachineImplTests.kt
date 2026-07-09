package build.wallet.statemachine.account.create.full.hardware

import app.cash.turbine.plusAssign
import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId
import build.wallet.analytics.v1.Action.*
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.W3OnboardingFeatureFlag
import build.wallet.firmware.HardwareUnlockInfoServiceFake
import build.wallet.firmware.UnlockMethod
import build.wallet.nfc.transaction.PairingTransactionProviderFake
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.nfc.transaction.PairingTransactionResponse.*
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.first
import okio.ByteString.Companion.encodeUtf8

// Large end-to-end coverage for hardware pairing; splitting would hurt cohesion.
@Suppress("LargeClass")
class PairNewHardwareUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)

  val pairingTransactionProvider = PairingTransactionProviderFake()

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  val helpCenterUiStateMachine =
    object : HelpCenterUiStateMachine,
      ScreenStateMachineMock<HelpCenterUiProps>("help-center") {}

  val appSessionManager = AppSessionManagerFake()
  val deviceInfoProvider = DeviceInfoProviderMock()
  val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()

  val featureFlagDao = FeatureFlagDaoFake()
  val w3OnboardingFeatureFlag = W3OnboardingFeatureFlag(featureFlagDao)
  val accountConfigService = AccountConfigServiceFake()

  fun createStateMachine() =
    PairNewHardwareUiStateMachineImpl(
      eventTracker = eventTracker,
      pairingTransactionProvider = pairingTransactionProvider,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      helpCenterUiStateMachine = helpCenterUiStateMachine,
      appSessionManager = appSessionManager,
      deviceInfoProvider = deviceInfoProvider,
      hardwareUnlockInfoService = hardwareUnlockInfoService,
      w3OnboardingFeatureFlag = w3OnboardingFeatureFlag,
      accountConfigService = accountConfigService,
    )

  val stateMachine = createStateMachine()

  val onSuccessCalls = turbines.create<FingerprintEnrolled>("on success calls")

  val onExitCalls = turbines.create<Unit>("on exit calls")

  val sealedCsekMock = "sealedCsek".encodeUtf8()
  val sealedSsekMock = "sealedSsek".encodeUtf8()

  val fingerprintEnrolled = FingerprintEnrolled(
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    keyBundle = HwKeyBundleMock,
    sealedCsek = sealedCsekMock,
    sealedSsek = sealedSsekMock,
    serial = "123",
    hardwareType = HardwareType.W1
  )

  val props = PairNewHardwareProps(
    request = PairNewHardwareProps.Request.Ready(
      appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
      onSuccess = onSuccessCalls::add
    ),
    screenPresentationStyle = Modal,
    onExit = {
      onExitCalls += Unit
    },
    eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION,
    pairingContext = PairingContext.Onboarding
  )

  fun incompleteFingerprintErrorCause() =
    HardwareFingerprintEnrollmentScreenModel(
      onSaveFingerprint = {},
      onBack = null,
      showingIncompleteEnrollmentError = true,
      incompleteEnrollmentErrorOnPrimaryButtonClick = {},
      onErrorOverlayClosed = {},
      eventTrackerContext = props.eventTrackerContext,
      presentationStyle = props.screenPresentationStyle,
      isNavigatingBack = false,
      headline = "Save your fingerprint",
      instructions = "Keep scanning your fingerprint."
    )
      .bottomSheetModel.shouldNotBeNull()
      .body.shouldBeInstanceOf<FormBodyModel>()
      .errorData.shouldNotBeNull()
      .cause

  beforeTest {
    accountConfigService.reset()
    appSessionManager.reset()
    appSessionManager.currentSessionId = "session-id"
    deviceInfoProvider.reset()
    hardwareUnlockInfoService.clear()
    featureFlagDao.reset()
    pairingTransactionProvider.reset()
  }

  test("incomplete fingerprint scan error uses stable cause") {
    incompleteFingerprintErrorCause()
      .shouldBeSameInstanceAs(incompleteFingerprintErrorCause())
  }

  test("pairing new wallet ui -- success") {
    stateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))

      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
      hardwareUnlockInfoService.countUnlockInfo(UnlockMethod.BIOMETRICS)
        .first().shouldBe(1)
    }
  }

  test("pairing new wallet ui -- fingerprint already enrolled") {
    stateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))

      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
      hardwareUnlockInfoService.countUnlockInfo(UnlockMethod.BIOMETRICS)
        .first().shouldBe(1)
    }
  }

  test("pairing new wallet ui -- fingerprint not enrolled overlay closed") {
    stateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
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

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintNotEnrolled(hardwareType = HardwareType.W1))
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
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
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

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintNotEnrolled(hardwareType = HardwareType.W1))
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
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
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
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

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
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
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
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

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      with(awaitItem()) {
        body.eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(
            PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
          )
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
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
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        onBack.shouldNotBeNull().invoke()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
      }
    }
  }

  test("pairing new wallet ui -- cancel start fingerprint enrollment") {
    stateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitBodyMock<NfcSessionUIStateMachineProps<Boolean>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onCancel()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
      }
    }
  }

  test("pairing new wallet ui -- back from showing fingerprint enrollment instructions") {
    stateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        onBack.shouldNotBeNull().invoke()
      }

      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
      }
    }
  }

  test("pairing new wallet ui -- back from showing activation instructions") {
    stateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        onBack.shouldNotBeNull().invoke()
      }

      onExitCalls.awaitItem()
    }
  }

  test("pairing W1 hardware -- second tap carries W1 hardwareTypeOverride") {
    stateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        primaryButton.onClick()
      }

      awaitBody<PairNewHardwareBodyModel> {
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap — detects W1 hardware
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      awaitBody<PairNewHardwareBodyModel> {
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      // Second NFC tap — should carry W1 as hardwareTypeOverride
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        config.hardwareTypeOverride.shouldBe(HardwareType.W1)
        onSuccess(fingerprintEnrolled)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))
      onSuccessCalls.awaitItem()
    }
  }

  // W3 Onboarding Flow Tests

  test("W3 onboarding -- shows activation instructions V2 screen when flag is enabled") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      awaitItem().apply {
        themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
        body.shouldBeInstanceOf<PairNewHardwareBodyModel>().apply {
          eventTrackerScreenInfo.shouldNotBeNull()
            .eventTrackerScreenId
            .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
          header.headline.shouldBe("Set up your Bitkey")
          header.sublineModel.shouldNotBeNull().string.shouldBe(
            "Tap the fingerprint sensor to wake your device.\nScan your Bitkey with your phone to get started."
          )
          primaryButton.treatment.shouldBe(ButtonModel.Treatment.BitkeyInteraction)
          secondaryButton.shouldBe(null)
        }
      }
    }
  }

  test("W3 onboarding -- tapping continue goes directly to NFC") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))

      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }

  test("W3 onboarding -- activation instructions V2 does not show legacy no-screen button") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        secondaryButton.shouldBe(null)
      }
    }
  }

  test("W3 onboarding -- back from activation instructions V2 exits") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        onBack.shouldNotBeNull().invoke()
      }

      onExitCalls.awaitItem()
    }
  }

  test("W3 onboarding -- cancel start fingerprint enrollment returns to activation instructions V2") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Activation instructions V2
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // NFC session - cancel
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onCancel()
      }

      // Should return to activation instructions V2, not the legacy pair instructions
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
      }
    }
  }

  test("W3 onboarding -- flag disabled shows legacy activation instructions") {
    w3OnboardingFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
    val legacyStateMachine = createStateMachine()

    legacyStateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
      }
    }
  }

  test("lost hardware recovery -- second tap passes shouldLockHardware") {
    val lostHwProps = props.copy(pairingContext = PairingContext.LostHardware)

    stateMachine.test(lostHwProps) {
      awaitBody<PairNewHardwareBodyModel> { primaryButton.onClick() }
      awaitBody<PairNewHardwareBodyModel> { primaryButton.onClick() }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      awaitBody<PairNewHardwareBodyModel> { primaryButton.onClick() }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      // Second NFC tap — shouldLockHardware = true for LostHardware.
      // Locking and W3 confirmation are handled inside session() on FingerprintEnrolled.
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      pairingTransactionProvider.latestInvokeParams.shouldNotBeNull()
        .shouldLockHardware.shouldBe(true)

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))
      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }

  test("lost hardware recovery -- first tap fingerprint enrolled passes shouldLockHardware") {
    val lostHwProps = props.copy(pairingContext = PairingContext.LostHardware)

    stateMachine.test(lostHwProps) {
      awaitBody<PairNewHardwareBodyModel> { primaryButton.onClick() }
      awaitBody<PairNewHardwareBodyModel> { primaryButton.onClick() }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap already returns FingerprintEnrolled — shouldLockHardware must
      // still be true for LostHardware.
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      pairingTransactionProvider.latestInvokeParams.shouldNotBeNull()
        .shouldLockHardware.shouldBe(true)

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))
      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }

  test("onboarding -- second tap does not lock hardware") {
    stateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> { primaryButton.onClick() }
      awaitBody<PairNewHardwareBodyModel> { primaryButton.onClick() }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      awaitBody<PairNewHardwareBodyModel> { primaryButton.onClick() }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      // Second NFC tap — shouldLockHardware = false for onboarding.
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      pairingTransactionProvider.latestInvokeParams.shouldNotBeNull()
        .shouldLockHardware.shouldBe(false)

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))
      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }
})
