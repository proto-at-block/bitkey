package build.wallet.statemachine.recovery.cloud

import bitkey.privilegedactions.ActionProofServiceFake
import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.auth.AuthKeyRotationFailure
import build.wallet.auth.FullAccountAuthKeyRotationServiceMock
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.HwAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.nfc.platform.RotateAppAuthKeysCompositeResult
import build.wallet.nfc.transaction.ProvisionAppAuthKeyTransactionProviderFake
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.RefreshAuthTokensProps
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachineMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.matchers.shouldBeDisabled
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class RotateAuthKeyUIStateMachineImplTests : FunSpec({

  val proofOfPossessionUIStateMachine =
    object : ProofOfPossessionNfcStateMachine,
      ScreenStateMachineMock<ProofOfPossessionNfcProps>(id = "hw-proof-of-possession") {}

  val refreshAuthTokensUiStateMachine =
    object : RefreshAuthTokensUiStateMachine,
      ScreenStateMachineMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {}

  val nfcConfirmableSessionUiStateMachine =
    NfcConfirmableSessionUiStateMachineMock(id = "nfc-confirmable-session")

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {}

  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceMock(turbines::create)
  val appKeysGenerator = AppKeysGeneratorMock()
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val actionProofService = ActionProofServiceFake()
  val provisionAppAuthKeyTransactionProvider = ProvisionAppAuthKeyTransactionProviderFake()

  val stateMachine = RotateAuthKeyUIStateMachineImpl(
    appKeysGenerator = appKeysGenerator,
    proofOfPossessionNfcStateMachine = proofOfPossessionUIStateMachine,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
    inAppBrowserNavigator = inAppBrowserNavigator,
    refreshAuthTokensUiStateMachine = refreshAuthTokensUiStateMachine,
    nfcConfirmableSessionUiStateMachine = nfcConfirmableSessionUiStateMachine,
    actionProofService = actionProofService,
    provisionAppAuthKeyTransactionProvider = provisionAppAuthKeyTransactionProvider,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine
  )

  val onBackCalls = turbines.create<Unit>("onBack calls")

  val props = RotateAuthKeyUIStateMachineProps(
    account = FullAccountMock,
    origin = RotateAuthKeyUIOrigin.PendingAttempt(
      attempt = PendingAuthKeyRotationAttempt.ProposedAttempt
    )
  )

  beforeTest {
    fullAccountAuthKeyRotationService.reset()
    actionProofService.reset()
    provisionAppAuthKeyTransactionProvider.reset()
  }

  test("deactivate other devices -- success") {
    stateMachine.test(props) {
      // Initial loading state
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        secondaryButton
          .shouldNotBeNull()
          .shouldBeDisabled()
      }
      // Kick Other People Out
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        clickSecondaryButton()
      }

      awaitBodyMock<ProofOfPossessionNfcProps>(
        id = "hw-proof-of-possession"
      ) {
        request.shouldBeTypeOf<Request.HwKeyProofAndAccountSignature>()
        (request as Request.HwKeyProofAndAccountSignature).onSuccess(
          "",
          HwAuthSecp256k1PublicKeyMock,
          HwFactorProofOfPossession(""),
          AppGlobalAuthKeyHwSignatureMock
        )
      }

      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.ROTATING_AUTH)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      // Provisioning hardware with new app auth key
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Unit>>(id = "nfc-session") {
        onSuccess(Unit)
      }

      awaitUntilBody<FormBodyModel> {
        this.id.shouldBe(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH)
      }
    }
  }

  test("provision cancel still succeeds") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().shouldBeDisabled()
      }
      awaitBody<FormBodyModel> {
        clickSecondaryButton()
      }

      awaitBodyMock<ProofOfPossessionNfcProps>(id = "hw-proof-of-possession") {
        (request as Request.HwKeyProofAndAccountSignature).onSuccess(
          "",
          HwAuthSecp256k1PublicKeyMock,
          HwFactorProofOfPossession(""),
          AppGlobalAuthKeyHwSignatureMock
        )
      }

      awaitBody<LoadingSuccessBodyModel> {
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      // Cancel provisioning — should still show success
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        onCancel()
      }

      awaitUntilBody<FormBodyModel> {
        this.id.shouldBe(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH)
      }
    }
  }

  test("deactivate other devices -- failure") {
    stateMachine.test(props) {
      fullAccountAuthKeyRotationService.rotationResult.value = { request, _ ->
        Err(AuthKeyRotationFailure.Unexpected(retryRequest = request))
      }

      // Initial loading state
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        secondaryButton
          .shouldNotBeNull()
          .shouldBeDisabled()
      }
      // Kick Other People Out
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        clickSecondaryButton()
      }

      awaitBodyMock<ProofOfPossessionNfcProps>(
        id = "hw-proof-of-possession"
      ) {
        request.shouldBeTypeOf<Request.HwKeyProofAndAccountSignature>()
        (request as Request.HwKeyProofAndAccountSignature).onSuccess(
          "",
          HwAuthSecp256k1PublicKeyMock,
          HwFactorProofOfPossession(""),
          AppGlobalAuthKeyHwSignatureMock
        )
      }

      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.ROTATING_AUTH)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      awaitBody<FormBodyModel> {
        this.id.shouldBe(InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_UNEXPECTED)
        primaryButton.shouldNotBeNull()
        secondaryButton.shouldNotBeNull()
      }
    }
  }

  test("don't deactivate other devices") {
    stateMachine.test(props) {
      // Initial loading state
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        secondaryButton
          .shouldNotBeNull()
          .shouldBeDisabled()
      }
      // Don't kick Other People Out
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull()
        clickPrimaryButton()
      }

      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.DISMISS_ROTATION_PROPOSAL)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("close button on unexpected failure dismisses pending attempt when origin is PendingAttempt") {
    stateMachine.test(props) {
      fullAccountAuthKeyRotationService.rotationResult.value = { request, _ ->
        Err(AuthKeyRotationFailure.Unexpected(retryRequest = request))
      }

      // Initial loading state
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().shouldBeDisabled()
      }

      // Kick Other People Out
      awaitBody<FormBodyModel> {
        clickSecondaryButton()
      }

      awaitBodyMock<ProofOfPossessionNfcProps>(id = "hw-proof-of-possession") {
        (request as Request.HwKeyProofAndAccountSignature).onSuccess(
          "",
          HwAuthSecp256k1PublicKeyMock,
          HwFactorProofOfPossession(""),
          AppGlobalAuthKeyHwSignatureMock
        )
      }

      awaitBody<LoadingSuccessBodyModel> {
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      // Unexpected failure screen - click close button
      awaitBody<FormBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_UNEXPECTED)
        onBack.shouldNotBeNull().invoke()
      }

      // Should transition to dismissing proposed attempt
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.DISMISS_ROTATION_PROPOSAL)
      }
    }
  }

  test("close button on unexpected failure calls onBack when origin is Settings") {
    val settingsProps = RotateAuthKeyUIStateMachineProps(
      account = FullAccountMock,
      origin = RotateAuthKeyUIOrigin.Settings(onBack = { onBackCalls.add(Unit) })
    )

    stateMachine.test(settingsProps) {
      fullAccountAuthKeyRotationService.rotationResult.value = { request, _ ->
        Err(AuthKeyRotationFailure.Unexpected(retryRequest = request))
      }

      // Initial loading state
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().shouldBeDisabled()
      }

      // Keys generated - click remove all other devices
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<ProofOfPossessionNfcProps>(id = "hw-proof-of-possession") {
        (request as Request.HwKeyProofAndAccountSignature).onSuccess(
          "",
          HwAuthSecp256k1PublicKeyMock,
          HwFactorProofOfPossession(""),
          AppGlobalAuthKeyHwSignatureMock
        )
      }

      awaitBody<LoadingSuccessBodyModel> {
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      // Unexpected failure screen - click close button
      awaitBody<FormBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_UNEXPECTED)
        onBack.shouldNotBeNull().invoke()
      }

      onBackCalls.awaitItem()
    }
  }

  test("close button on account locked failure dismisses pending attempt when origin is PendingAttempt") {
    stateMachine.test(props) {
      fullAccountAuthKeyRotationService.rotationResult.value = { request, _ ->
        Err(AuthKeyRotationFailure.AccountLocked(retryRequest = request))
      }

      // Initial loading state
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().shouldBeDisabled()
      }

      // Kick Other People Out
      awaitBody<FormBodyModel> {
        clickSecondaryButton()
      }

      awaitBodyMock<ProofOfPossessionNfcProps>(id = "hw-proof-of-possession") {
        (request as Request.HwKeyProofAndAccountSignature).onSuccess(
          "",
          HwAuthSecp256k1PublicKeyMock,
          HwFactorProofOfPossession(""),
          AppGlobalAuthKeyHwSignatureMock
        )
      }

      awaitBody<LoadingSuccessBodyModel> {
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      // Account locked failure screen - click close button
      awaitBody<FormBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_ACCOUNT_LOCKED)
        onBack.shouldNotBeNull().invoke()
      }

      // Should transition to dismissing proposed attempt
      awaitBody<LoadingSuccessBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.DISMISS_ROTATION_PROPOSAL)
      }
    }
  }

  test("close button on account locked failure calls onBack when origin is Settings") {
    val settingsProps = RotateAuthKeyUIStateMachineProps(
      account = FullAccountMock,
      origin = RotateAuthKeyUIOrigin.Settings(onBack = { onBackCalls.add(Unit) })
    )

    stateMachine.test(settingsProps) {
      fullAccountAuthKeyRotationService.rotationResult.value = { request, _ ->
        Err(AuthKeyRotationFailure.AccountLocked(retryRequest = request))
      }

      // Initial loading state
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().shouldBeDisabled()
      }

      // Keys generated - click remove all other devices
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }

      awaitBodyMock<ProofOfPossessionNfcProps>(id = "hw-proof-of-possession") {
        (request as Request.HwKeyProofAndAccountSignature).onSuccess(
          "",
          HwAuthSecp256k1PublicKeyMock,
          HwFactorProofOfPossession(""),
          AppGlobalAuthKeyHwSignatureMock
        )
      }

      awaitBody<LoadingSuccessBodyModel> {
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      // Account locked failure screen - click close button
      awaitBody<FormBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_ACCOUNT_LOCKED)
        onBack.shouldNotBeNull().invoke()
      }

      onBackCalls.awaitItem()
    }
  }

  // -- W3 path tests --

  test("W3 deactivate other devices -- success") {
    val w3Props = RotateAuthKeyUIStateMachineProps(
      account = FullAccountW3Mock,
      origin = RotateAuthKeyUIOrigin.PendingAttempt(
        attempt = PendingAuthKeyRotationAttempt.ProposedAttempt
      )
    )

    stateMachine.test(w3Props) {
      // Initial loading state (keys generating)
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        secondaryButton
          .shouldNotBeNull()
          .shouldBeDisabled()
      }
      // Keys generated — kick other people out
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        clickSecondaryButton()
      }

      // W3 step 1: refreshing auth tokens
      awaitUntilBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
        onSuccess(AccountAuthTokensMock)
      }

      // W3 step 2: building payload auto-transitions with fakes
      // W3 step 3: composite NFC tap
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<RotateAppAuthKeysCompositeResult>>(
        id = "nfc-confirmable-session"
      ) {
        onSuccess(
          RotateAppAuthKeysCompositeResult(
            actionProofSignature = "ab".repeat(64),
            appGlobalAuthKeyHwSignature = "cd".repeat(64),
            hwSignedAccountId = "ef".repeat(64),
            hwAuthPublicKey = HwAuthPublicKeyMock
          )
        )
      }

      // Rotating auth keys
      awaitUntilBody<LoadingSuccessBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.ROTATING_AUTH)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      // Provisioning hardware
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Unit>>(id = "nfc-session") {
        onSuccess(Unit)
      }

      // Success
      awaitUntilBody<FormBodyModel> {
        this.id.shouldBe(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH)
      }
    }
  }

  test("W3 deactivate other devices -- rotation failure") {
    val w3Props = RotateAuthKeyUIStateMachineProps(
      account = FullAccountW3Mock,
      origin = RotateAuthKeyUIOrigin.PendingAttempt(
        attempt = PendingAuthKeyRotationAttempt.ProposedAttempt
      )
    )

    stateMachine.test(w3Props) {
      fullAccountAuthKeyRotationService.rotationResult.value = { request, _ ->
        Err(AuthKeyRotationFailure.Unexpected(retryRequest = request))
      }

      // Initial loading → keys generated
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().shouldBeDisabled()
      }
      awaitBody<FormBodyModel> {
        clickSecondaryButton()
      }

      // Refresh tokens
      awaitUntilBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
        onSuccess(AccountAuthTokensMock)
      }

      // Composite NFC tap
      awaitUntilBodyMock<NfcConfirmableSessionUIStateMachineProps<RotateAppAuthKeysCompositeResult>>(
        id = "nfc-confirmable-session"
      ) {
        onSuccess(
          RotateAppAuthKeysCompositeResult(
            actionProofSignature = "ab".repeat(64),
            appGlobalAuthKeyHwSignature = "cd".repeat(64),
            hwSignedAccountId = "ef".repeat(64),
            hwAuthPublicKey = HwAuthPublicKeyMock
          )
        )
      }

      // Rotating auth keys
      awaitUntilBody<LoadingSuccessBodyModel> {
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      // Unexpected failure
      awaitUntilBody<FormBodyModel> {
        this.id.shouldBe(InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_UNEXPECTED)
        primaryButton.shouldNotBeNull()
        secondaryButton.shouldNotBeNull()
      }
    }
  }
})
