package build.wallet.statemachine.account.create.full.keybox.create

import app.cash.turbine.plusAssign
import bitkey.recovery.DescriptorBackupError
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.EncryptedDescriptorBackupsFeatureFlag
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.onboarding.CreateFullAccountContext.NewFullAccount
import build.wallet.onboarding.OnboardFullAccountServiceFake
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString

class CreateKeyboxUiStateMachineImplTests : FunSpec({

  val createFullAccountService = OnboardFullAccountServiceFake()
  val descriptorBackupService = DescriptorBackupServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val encryptedDescriptorBackupsFeatureFlag = EncryptedDescriptorBackupsFeatureFlag(featureFlagDao)

  val stateMachine = CreateKeyboxUiStateMachineImpl(
    onboardFullAccountService = createFullAccountService,
    pairNewHardwareUiStateMachine = object : PairNewHardwareUiStateMachine,
      ScreenStateMachineMock<PairNewHardwareProps>(
        id = "hw-onboard"
      ) {},
    descriptorBackupService = descriptorBackupService,
    encryptedDescriptorBackupsFeatureFlag = encryptedDescriptorBackupsFeatureFlag
  )

  val onExitCalls = turbines.create<Unit>("onExit calls")
  val onAccountCreatedCalls = turbines.create<Unit>("on account created calls")

  val props = CreateKeyboxUiProps(
    context = NewFullAccount,
    onExit = { onExitCalls += Unit },
    onAccountCreated = { onAccountCreatedCalls += Unit }
  )

  beforeTest {
    createFullAccountService.reset()
    descriptorBackupService.reset()
    descriptorBackupService.uploadOnboardingDescriptorBackupResult = Ok(Unit)
    featureFlagDao.reset()
  }

  val hwActivation = FingerprintEnrolled(
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    keyBundle = HwKeyBundleMock,
    sealedCsek = ByteString.EMPTY,
    sealedSsek = ByteString.EMPTY,
    serial = "123"
  )

  test("happy path - account creation and descriptor upload succeed") {
    // Enable feature flag
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    stateMachine.test(props) {
      // creating app keys
      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Preparing>()
      }

      // Pairing with HW
      awaitBodyMock<PairNewHardwareProps>("hw-onboard") {
        val ready = request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        ready.onSuccess(hwActivation)
      }

      // Creating account with f8e
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Creating account...")
      }

      // Setting up wallet backup
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Setting up wallet backup...")
      }

      onAccountCreatedCalls.awaitItem()
    }
  }

  test("descriptor upload fails initially, then succeeds on retry") {
    // Set up descriptor upload to fail initially
    descriptorBackupService.uploadOnboardingDescriptorBackupResult =
      Err(DescriptorBackupError.NetworkError(RuntimeException("Network error")))

    // Enable feature flag
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    stateMachine.test(props) {
      // creating app keys
      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Preparing>()
      }

      // Pairing with HW
      awaitBodyMock<PairNewHardwareProps>("hw-onboard") {
        val ready = request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        ready.onSuccess(hwActivation)
      }

      // Creating account with f8e
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Creating account...")
      }

      // Setting up wallet backup
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Setting up wallet backup...")
      }

      // Descriptor upload fails - should show error
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t create your wallet")
        header?.sublineModel.shouldNotBeNull().string.shouldContain("Make sure you are connected to the internet")
        primaryButton?.text.shouldBe("Retry")

        // Set descriptor upload to succeed on retry
        descriptorBackupService.uploadOnboardingDescriptorBackupResult = Ok(Unit)

        // Click retry button
        primaryButton?.onClick?.invoke()
      }

      // Should show loading screen for descriptor upload retry
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Setting up wallet backup...")
      }

      // Should succeed after retry
      onAccountCreatedCalls.awaitItem()
    }
  }

  test("descriptor upload fails multiple times") {
    // Set up descriptor upload to always fail
    descriptorBackupService.uploadOnboardingDescriptorBackupResult =
      Err(DescriptorBackupError.SsekNotFound)

    // Enable feature flag
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    stateMachine.test(props) {
      // creating app keys
      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Preparing>()
      }

      // Pairing with HW
      awaitBodyMock<PairNewHardwareProps>("hw-onboard") {
        val ready = request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        ready.onSuccess(hwActivation)
      }

      // Creating account with f8e
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Creating account...")
      }

      // Setting up wallet backup
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Setting up wallet backup...")
      }

      // First failure - should show error
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t create your wallet")
        header?.sublineModel.shouldNotBeNull().string.shouldContain("We are looking into this. Please try again later.")
        primaryButton?.text.shouldBe("Retry")

        // Click retry button
        primaryButton?.onClick?.invoke()
      }

      // Should show loading screen for retry
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Setting up wallet backup...")
      }

      // Second failure - should show error again
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t create your wallet")
        header?.sublineModel.shouldNotBeNull().string.shouldContain("We are looking into this. Please try again later.")
        primaryButton?.text.shouldBe("Retry")
      }

      // Verify onAccountCreated was never called
      onAccountCreatedCalls.expectNoEvents()
    }
  }

  test("account creation fails - should not attempt descriptor upload") {
    // Set up account creation to fail
    createFullAccountService.createAccountResult = Err(RuntimeException("Account creation failed"))

    stateMachine.test(props) {
      // creating app keys
      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Preparing>()
      }

      // Pairing with HW
      awaitBodyMock<PairNewHardwareProps>("hw-onboard") {
        val ready = request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        ready.onSuccess(hwActivation)
      }

      // Creating account with f8e
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Creating account...")
      }

      // Should show account creation error
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t create your wallet")
        header?.sublineModel.shouldNotBeNull().string.shouldContain("We are looking into this. Please try again later.")
        primaryButton?.text.shouldBe("Retry")

        // Set descriptor upload to succeed on retry
        descriptorBackupService.uploadOnboardingDescriptorBackupResult = Ok(Unit)

        // Click retry button
        primaryButton?.onClick?.invoke()
      }

      // Verify onAccountCreated was never called due to account creation failure
      onAccountCreatedCalls.expectNoEvents()
    }
  }

  test("descriptor upload fails with network error shows connectivity message") {
    // Set up descriptor upload to fail with network error
    descriptorBackupService.uploadOnboardingDescriptorBackupResult =
      Err(DescriptorBackupError.NetworkError(RuntimeException("Connection failed")))

    // Enable feature flag
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    stateMachine.test(props) {
      // creating app keys
      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Preparing>()
      }

      // Pairing with HW
      awaitBodyMock<PairNewHardwareProps>("hw-onboard") {
        val ready = request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        ready.onSuccess(hwActivation)
      }

      // Creating account with f8e
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Creating account...")
      }

      // Setting up wallet backup
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Setting up wallet backup...")
      }

      // Descriptor upload fails - should show connectivity error message
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t create your wallet")
        header?.sublineModel.shouldNotBeNull().string.shouldContain("Make sure you are connected to the internet")
        primaryButton?.text.shouldBe("Retry")
      }

      onAccountCreatedCalls.expectNoEvents()
    }
  }

  test("descriptor upload fails with non-network error shows generic message") {
    // Set up descriptor upload to fail with non-network error
    descriptorBackupService.uploadOnboardingDescriptorBackupResult =
      Err(DescriptorBackupError.SsekNotFound)

    // Enable feature flag
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

    stateMachine.test(props) {
      // creating app keys
      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Preparing>()
      }

      // Pairing with HW
      awaitBodyMock<PairNewHardwareProps>("hw-onboard") {
        val ready = request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        ready.onSuccess(hwActivation)
      }

      // Creating account with f8e
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Creating account...")
      }

      // Setting up wallet backup
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Setting up wallet backup...")
      }

      // Descriptor upload fails - should show generic error message
      awaitBody<FormBodyModel> {
        header?.headline.shouldBe("We couldn’t create your wallet")
        header?.sublineModel.shouldNotBeNull().string.shouldContain("We are looking into this. Please try again later.")
        primaryButton?.text.shouldBe("Retry")
      }

      onAccountCreatedCalls.expectNoEvents()
    }
  }

  test("feature flag disabled - skips descriptor upload") {
    // Disable feature flag
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

    stateMachine.test(props) {
      // creating app keys
      awaitBodyMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Preparing>()
      }

      // Pairing with HW
      awaitBodyMock<PairNewHardwareProps>("hw-onboard") {
        val ready = request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        ready.onSuccess(hwActivation)
      }

      // Creating account with f8e
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        message.shouldBe("Creating account...")
      }

      // Should skip descriptor upload and directly call onAccountCreated
      onAccountCreatedCalls.awaitItem()
    }
  }
})
