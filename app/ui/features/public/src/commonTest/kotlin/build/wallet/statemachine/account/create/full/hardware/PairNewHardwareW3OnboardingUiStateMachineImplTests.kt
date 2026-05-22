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
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.W3OnboardingFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.firmware.HardwareUnlockInfoServiceFake
import build.wallet.nfc.transaction.PairingTransactionProviderFake
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.nfc.transaction.PairingTransactionResponse.*
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconBackgroundType.Circle.CircleColor.Foreground10
import build.wallet.ui.model.icon.IconBackgroundType.Circle.CircleColor.Secondary
import build.wallet.ui.model.icon.IconSize.Regular
import build.wallet.ui.model.icon.IconTint.Foreground
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okio.ByteString.Companion.encodeUtf8

/**
 * Tests for W3 hardware onboarding flow in PairNewHardwareUiStateMachine.
 * These tests verify the new two-tap W3 flow and hardware type detection/switching.
 */
class PairNewHardwareW3OnboardingUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)

  val pairingTransactionProvider = PairingTransactionProviderFake()

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  val helpCenterUiStateMachine =
    object : HelpCenterUiStateMachine,
      ScreenStateMachineMock<HelpCenterUiProps>("help-center") {}

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
      hardwareUnlockInfoService = hardwareUnlockInfoService,
      w3OnboardingFeatureFlag = w3OnboardingFeatureFlag,
      accountConfigService = accountConfigService,
    )

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

  beforeTest {
    hardwareUnlockInfoService.clear()
    featureFlagDao.reset()
  }

  // W3 Onboarding Flow Tests

  test("W3 onboarding -- shows activation instructions V2 screen when flag enabled and hardware is W3") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
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

  test("W3 onboarding -- tapping continue goes directly to NFC with hardwareType W3") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap returns FingerprintEnrolled (hardware already has fingerprint)
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      // Should complete directly
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))
      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }

  test("W3 onboarding -- activation instructions V2 does not show legacy no-screen button") {
    w3OnboardingFeatureFlag.setFlagValue(true)
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
    w3OnboardingFeatureFlag.setFlagValue(true)
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

  test("W3 onboarding -- flag disabled shows legacy activation instructions") {
    w3OnboardingFeatureFlag.setFlagValue(false)
    val legacyStateMachine = createStateMachine()

    legacyStateMachine.test(props) {
      // Should show legacy flow even with W3 hardware when flag is off
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
      }
    }
  }

  test("W3 onboarding -- cancel from NFC returns to activation instructions V2") {
    w3OnboardingFeatureFlag.setFlagValue(true)
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
        onCancel()
      }

      // Cancel from NFC in W3 flow returns to activation instructions V2
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
      }
    }
  }

  // W3 Two-Tap Flow Tests

  test("W3 onboarding -- first NFC tap with FingerprintEnrollmentStarted shows Finished On Your Device screen") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Start at Let's get set up
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap - returns FingerprintEnrollmentStarted with W3 hardware
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // Should show "Finished on your device?" screen
      awaitItem().apply {
        themePreference.shouldBe(ThemePreference.System)
        body.shouldBeInstanceOf<CompleteTwoTapBodyModel>().apply {
          eventTrackerScreenInfo.shouldNotBeNull()
            .eventTrackerScreenId
            .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
          header?.headline.shouldBe("Finished on your device?")
          primaryButton?.text.shouldBe("Yes, continue")
          designSystemV2Model.shouldNotBeNull().apply {
            useDesignSystemV2ScreenLayout.shouldBe(true)
            scrollable.shouldBe(false)
            mainContentVerticalAlignment.shouldBe(FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER)
          }
          toolbar.shouldNotBeNull().leadingAccessory.shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
            .model.iconModel.apply {
              iconBackgroundType.shouldBe(Circle(circleSize = Regular, color = Secondary))
              iconTint.shouldBe(Foreground)
            }
          toolbar.shouldNotBeNull().trailingAccessory.shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
            .model.iconModel.apply {
              iconBackgroundType.shouldBe(Circle(circleSize = Regular, color = Secondary))
              iconTint.shouldBe(Foreground)
            }
          designSystemV2Model.shouldNotBeNull().toolbar.shouldNotBeNull().leadingAccessory
            .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
            .model.iconModel.apply {
              iconBackgroundType.shouldBe(Circle(circleSize = Regular, color = Foreground10))
              iconTint.shouldBeNull()
            }
          designSystemV2Model.shouldNotBeNull().toolbar.shouldNotBeNull().trailingAccessory
            .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
            .model.iconModel.apply {
              iconBackgroundType.shouldBe(Circle(circleSize = Regular, color = Foreground10))
              iconTint.shouldBeNull()
            }
          }
      }
    }
  }

  test("W3 onboarding -- two-tap flow completes fingerprint enrollment") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Start at Let's get set up
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap - returns FingerprintEnrollmentStarted with W3 hardware
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // "Finished on your device?" screen - tap continue
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
        onContinue()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      // Second NFC tap - should carry W3 hardwareTypeOverride from first tap detection
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        config.hardwareTypeOverride.shouldBe(HardwareType.W3)
        onSuccess(fingerprintEnrolled)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))

      // Flow completes
      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }

  test("W3 onboarding -- back from Finished On Your Device exits onboarding") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Start at activation instructions V2
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap - returns FingerprintEnrollmentStarted with W3 hardware
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // "Finished on your device?" screen - go back should exit onboarding
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
        onBack.shouldNotBeNull().invoke()
      }

      // Should exit onboarding entirely
      onExitCalls.awaitItem()
    }
  }

  test("W3 onboarding -- FingerprintNotEnrolled shows Finished On Your Device screen") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Start at Let's get set up
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap - returns FingerprintNotEnrolled with W3 hardware (enrollment was started but incomplete)
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintNotEnrolled(hardwareType = HardwareType.W3))
      }

      // Should show "Finished on your device?" screen (W3 UI)
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
        header?.headline.shouldBe("Finished on your device?")
      }
    }
  }

  test("Legacy flow -- FingerprintEnrollmentStarted shows legacy fingerprint instructions") {
    w3OnboardingFeatureFlag.setFlagValue(false)
    val legacyStateMachine = createStateMachine()

    legacyStateMachine.test(props) {
      // Start at activation instructions
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      // Pair instructions screen
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap - returns FingerprintEnrollmentStarted
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      // Should show legacy fingerprint instructions (not "Have you completed?")
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
      }
    }
  }

  // W3 How To Add Your Fingerprint Help Screen Tests

  test("W3 onboarding -- help button from Finished On Your Device shows How To Add Fingerprint screen") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Start at Let's get set up
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap - returns FingerprintEnrollmentStarted with W3 hardware
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // "Finished on your device?" screen - tap help button
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
        onHelpClick()
      }

      // Should show fingerprint help screen
      awaitBody<FormBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_FINGERPRINT_ENROLLMENT_HELP)
        header.shouldNotBeNull().headline.shouldBe("How it works")
      }
    }
  }

  test("W3 onboarding -- back from How To Add Fingerprint returns to Finished On Your Device screen") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Start at Let's get set up
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap - returns FingerprintEnrollmentStarted with W3 hardware
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // "Finished on your device?" screen - tap help button
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
        onHelpClick()
      }

      // Fingerprint help screen - go back
      awaitBody<FormBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_FINGERPRINT_ENROLLMENT_HELP)
        onBack.shouldNotBeNull().invoke()
      }

      // Should return to "Finished on your device?" screen
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
      }
    }
  }

  test("W3 onboarding -- can continue flow after viewing How To Add Fingerprint help") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Start at Let's get set up
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap - returns FingerprintEnrollmentStarted with W3 hardware
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // "Finished on your device?" screen - tap help button
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
        onHelpClick()
      }

      // View help screen then go back
      awaitBody<FormBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_FINGERPRINT_ENROLLMENT_HELP)
        onBack.shouldNotBeNull().invoke()
      }

      // Back at "Finished on your device?" - now continue the flow
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
        onContinue()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      // Second NFC tap - returns FingerprintEnrolled
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))

      // Flow completes
      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }

  test("W3 onboarding -- How To Add Fingerprint screen has close button in toolbar that returns to previous screen") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Start at Let's get set up
      awaitBody<PairNewHardwareBodyModel> {
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem()

      // First NFC tap - returns FingerprintEnrollmentStarted with W3 hardware
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // "Finished on your device?" screen - tap help button
      awaitBody<CompleteTwoTapBodyModel> {
        onHelpClick()
      }

      // Fingerprint help screen - verify toolbar has close button and click it
      awaitBody<FormBodyModel> {
        // Verify toolbar exists with leading close accessory
        toolbar.shouldNotBeNull()
          .leadingAccessory.shouldNotBeNull()

        // Click the close button via toolbar leading accessory
        toolbar.shouldNotBeNull()
          .leadingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick.invoke()
      }

      // Should return to "Finished on your device?" screen
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
      }
    }
  }

  // Hardware Type Detection Tests

  test("expected W3 hardware type but detected W1 -- silently switches to legacy flow") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    w3StateMachine.test(props) {
      // Start with W3 activation instructions (expected hardware type is W3)
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap with expected type W3, but device firmware reports W1
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        // The device reports it's actually W1 hardware
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      // Should silently switch to legacy W1 flow - show legacy fingerprint instructions
      // (not W3 "Finished on your device?" screen)
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      // Second NFC tap should use detected W1 hardware type
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolled)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))

      // Flow completes successfully with W1 hardware
      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolled)
    }
  }

  test("expected W1 hardware type but detected W3 -- silently switches to W3 two-tap flow") {
    w3OnboardingFeatureFlag.setFlagValue(false)
    val legacyStateMachine = createStateMachine()

    val fingerprintEnrolledW3 = fingerprintEnrolled.copy(hardwareType = HardwareType.W3)

    legacyStateMachine.test(props) {
      // Start with legacy W1 activation instructions (expected hardware type is W1)
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS)
        primaryButton.onClick()
      }

      // Legacy W1 flow shows fingerprint enrollment instructions screen
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS)
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap with expected type W1, but device firmware reports W3
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        // The device reports it's actually W3 hardware
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // Should silently switch to W3 flow - show "Finished on your device?" screen
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
        header?.headline.shouldBe("Finished on your device?")
        onContinue()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      // Second NFC tap should use detected W3 hardware type
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolledW3)
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))

      // Flow completes successfully with W3 hardware
      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolledW3)
    }
  }

  // W3 Upgrade Hardware Type Enforcement Tests

  test("W3Upgrade context -- first tap detects W1 instead of W3 shows wrong hardware error with retry") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    val w3UpgradeProps = props.copy(pairingContext = PairingContext.W3Upgrade)

    w3StateMachine.test(w3UpgradeProps) {
      // Start at activation instructions V2
      awaitBody<PairNewHardwareBodyModel> {
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap — device reports W1 instead of W3
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      // Should show wrong hardware error instead of silently switching
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("Wrong Bitkey tapped")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
        primaryButton.shouldNotBeNull().onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // Retry goes back to NFC — this time tap the correct W3 device
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // Should proceed normally to "Finished on your device?" screen
      awaitBody<CompleteTwoTapBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP)
      }
    }
  }

  test("W3Upgrade context -- second tap detects W1 instead of W3 shows wrong hardware error with retry") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    val w3UpgradeProps = props.copy(pairingContext = PairingContext.W3Upgrade)

    w3StateMachine.test(w3UpgradeProps) {
      // Start at activation instructions V2
      awaitBody<PairNewHardwareBodyModel> {
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap — correct W3 device
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W3))
      }

      // "Finished on your device?" screen
      awaitBody<CompleteTwoTapBodyModel> {
        onContinue()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_FINGERPRINT))

      // Second NFC tap — device reports W1 instead of W3
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(
          FingerprintEnrolled(
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            keyBundle = HwKeyBundleMock,
            sealedCsek = sealedCsekMock,
            sealedSsek = sealedSsekMock,
            serial = "123",
            hardwareType = HardwareType.W1
          )
        )
      }

      // Should show wrong hardware error
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("Wrong Bitkey tapped")
        primaryButton.shouldNotBeNull().text.shouldBe("Retry")
      }
    }
  }

  test("W3Upgrade context -- first tap detects W3 proceeds normally") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    val fingerprintEnrolledW3 = fingerprintEnrolled.copy(hardwareType = HardwareType.W3)
    val w3UpgradeProps = props.copy(pairingContext = PairingContext.W3Upgrade)

    w3StateMachine.test(w3UpgradeProps) {
      // Start at activation instructions V2
      awaitBody<PairNewHardwareBodyModel> {
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap — correct W3 device, fingerprint already enrolled
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(fingerprintEnrolledW3)
      }

      // Should complete directly without error
      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_FINGERPRINT_COMPLETE))
      onSuccessCalls.awaitItem().shouldBe(fingerprintEnrolledW3)
    }
  }

  test("Onboarding context -- first tap detects W1 silently switches (not blocked like W3Upgrade)") {
    w3OnboardingFeatureFlag.setFlagValue(true)
    val w3StateMachine = createStateMachine()

    // Uses Onboarding context, not W3Upgrade — should silently switch, not error
    w3StateMachine.test(props) {
      awaitBody<PairNewHardwareBodyModel> {
        primaryButton.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_HW_ONBOARDING_OPEN))

      // First NFC tap — W1 device
      awaitBodyMock<NfcSessionUIStateMachineProps<PairingTransactionResponse>>(
        id = nfcSessionUIStateMachine.id
      ) {
        onSuccess(FingerprintEnrollmentStarted(hardwareType = HardwareType.W1))
      }

      // Should silently switch to legacy flow (no error)
      awaitBody<PairNewHardwareBodyModel> {
        eventTrackerScreenInfo.shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS)
      }
    }
  }
})
