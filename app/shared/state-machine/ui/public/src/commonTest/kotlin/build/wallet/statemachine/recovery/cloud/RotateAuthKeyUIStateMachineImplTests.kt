package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.auth.AuthKeyRotationFailure
import build.wallet.auth.FullAccountAuthKeyRotationServiceMock
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
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

  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceMock(turbines::create)
  val appKeysGenerator = AppKeysGeneratorMock()
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val stateMachine = RotateAuthKeyUIStateMachineImpl(
    appKeysGenerator = appKeysGenerator,
    proofOfPossessionNfcStateMachine = proofOfPossessionUIStateMachine,
    fullAccountAuthKeyRotationService = fullAccountAuthKeyRotationService,
    inAppBrowserNavigator = inAppBrowserNavigator
  )

  val props = RotateAuthKeyUIStateMachineProps(
    account = FullAccountMock,
    origin = RotateAuthKeyUIOrigin.PendingAttempt(
      attempt = PendingAuthKeyRotationAttempt.ProposedAttempt
    )
  )

  beforeTest {
    fullAccountAuthKeyRotationService.reset()
  }

  test("deactivate other devices -- success") {
    stateMachine.test(props) {
      // Initial loading state
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        secondaryButton
          .shouldNotBeNull()
          .shouldBeDisabled()
      }
      // Kick Other People Out
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        clickSecondaryButton()
      }

      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps>(
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

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.ROTATING_AUTH)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      awaitScreenWithBody<FormBodyModel> {
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
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        secondaryButton
          .shouldNotBeNull()
          .shouldBeDisabled()
      }
      // Kick Other People Out
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        clickSecondaryButton()
      }

      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps>(
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

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.ROTATING_AUTH)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        fullAccountAuthKeyRotationService.rotateAuthKeysCalls.awaitItem()
      }

      awaitScreenWithBody<FormBodyModel> {
        this.id.shouldBe(InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_UNEXPECTED)
        primaryButton.shouldNotBeNull()
        secondaryButton.shouldNotBeNull()
      }
    }
  }

  test("don't deactivate other devices") {
    stateMachine.test(props) {
      // Initial loading state
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        secondaryButton
          .shouldNotBeNull()
          .shouldBeDisabled()
      }
      // Don't kick Other People Out
      awaitScreenWithBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull()
        clickPrimaryButton()
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        id.shouldBe(InactiveAppEventTrackerScreenId.DISMISS_ROTATION_PROPOSAL)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }
})
