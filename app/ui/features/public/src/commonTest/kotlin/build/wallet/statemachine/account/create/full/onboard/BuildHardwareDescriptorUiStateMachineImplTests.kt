package build.wallet.statemachine.account.create.full.onboard

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.BUILD_HARDWARE_DESCRIPTOR_INTRO
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.health.CloudBackupHealthRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.onboarding.HardwareDescriptorDeliveryServiceFake
import build.wallet.onboarding.OnboardingCompletionServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BuildHardwareDescriptorUiStateMachineImplTests : FunSpec({
  val hardwareDescriptorDeliveryService = HardwareDescriptorDeliveryServiceFake()
  val onboardingCompletionService = OnboardingCompletionServiceFake()
  val cloudBackupHealthRepository = CloudBackupHealthRepositoryMock(turbines::create)
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val featureFlagDao = FeatureFlagDaoFake()
  val designSystemUpdatesFeatureFlag = DesignSystemUpdatesFeatureFlag(featureFlagDao)

  val nfcSessionUIStateMachine = object :
    NfcSessionUIStateMachine,
    ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
      id = "nfc-session"
    ) {}

  val stateMachine = BuildHardwareDescriptorUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    hardwareDescriptorDeliveryService = hardwareDescriptorDeliveryService,
    cloudBackupHealthRepository = cloudBackupHealthRepository,
    keyboxDao = keyboxDao,
    onboardingCompletionService = onboardingCompletionService,
    designSystemUpdatesFeatureFlag = designSystemUpdatesFeatureFlag,
  )

  val onBack = turbines.create<Unit>("onBack")
  val onComplete = turbines.create<Unit>("onComplete")
  val onBackupFailed = turbines.create<Throwable>("onError")

  val props = BuildHardwareDescriptorUiProps(
    fullAccount = FullAccountMock,
    onBack = { onBack += Unit },
    onComplete = { onComplete += Unit },
    onError = { onBackupFailed += it }
  )

  beforeTest {
    hardwareDescriptorDeliveryService.reset()
    onboardingCompletionService.reset()
    featureFlagDao.reset()
  }

  test("completes onboarding and shows intro screen") {
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Ok { _, _ -> "fake-hw-signature" }

    stateMachine.test(props) {
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP) {
        message.shouldBe("Completing onboarding")
      }

      awaitUntilBody<FormBodyModel>(id = BUILD_HARDWARE_DESCRIPTOR_INTRO) {
        primaryButton?.text.shouldBe("Continue")
      }

      // Assert after awaiting the intro screen, since recordFallbackCompletion()
      // is called in the LaunchedEffect before transitioning to ShowingIntroScreen.
      onboardingCompletionService.recordFallbackCompletionCalled.shouldBe(true)
    }
  }

  test("calls onError when preparation fails") {
    val error = Error("Preparation failed")
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Err(error)

    stateMachine.test(props) {
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)
      onBackupFailed.awaitItem().shouldBe(error)
    }
  }

  test("transitions to NFC session and calls onComplete on success") {
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Ok { _, _ -> "fake-hw-signature" }

    stateMachine.test(props) {
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      awaitBody<FormBodyModel>(id = BUILD_HARDWARE_DESCRIPTOR_INTRO) {
        clickPrimaryButton()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>> {
        @Suppress("UNCHECKED_CAST")
        (this as NfcSessionUIStateMachineProps<Any>).onSuccess("fake-hw-signature")
      }

      cloudBackupHealthRepository.performSyncCalls.awaitItem()
      onComplete.awaitItem()
    }
  }

  test("returns to intro screen when NFC session is cancelled") {
    hardwareDescriptorDeliveryService.fetchSignatureAndPrepareNfcSessionResult = Ok { _, _ -> "fake-hw-signature" }

    stateMachine.test(props) {
      awaitUntilBody<LoadingSuccessBodyModel>(id = LOADING_ONBOARDING_STEP)

      awaitBody<FormBodyModel>(id = BUILD_HARDWARE_DESCRIPTOR_INTRO) {
        clickPrimaryButton()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>> {
        onCancel()
      }

      awaitBody<FormBodyModel>(id = BUILD_HARDWARE_DESCRIPTOR_INTRO)
    }
  }
})
