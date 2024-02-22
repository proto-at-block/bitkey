package build.wallet.statemachine.recovery.cloud

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.AuthEventTrackerScreenId
import build.wallet.auth.AuthKeyRotationManagerMock
import build.wallet.auth.AuthKeyRotationRequestState
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class RotateAuthKeyUIStateMachineImplTests : FunSpec({

  val proofOfPossessionUIStateMachine =
    object : ProofOfPossessionNfcStateMachine,
      ScreenStateMachineMock<ProofOfPossessionNfcProps>(id = "hw-proof-of-possession") {}

  val authKeyRotationManager = AuthKeyRotationManagerMock(turbines::create)

  val stateMachine = RotateAuthKeyUIStateMachineImpl(
    keyboxDao = KeyboxDaoMock(turbines::create),
    proofOfPossessionNfcStateMachine = proofOfPossessionUIStateMachine,
    authKeyRotationManager = authKeyRotationManager
  )

  val onKeyboxRotatedCalls = turbines.create<Unit>("cannot access cloud calls")

  val props = RotateAuthKeyUIStateMachineProps(
    keybox = KeyboxMock,
    origin = RotateAuthKeyUIOrigin.CloudRestore(
      onComplete = {
        onKeyboxRotatedCalls += Unit
      }
    )
  )

  beforeTest {
    authKeyRotationManager.reset()
  }

  test("deactivate other devices -- success") {
    stateMachine.test(props) {
      // Kick Other People Out
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps>(
        id = "hw-proof-of-possession"
      ) {
        request.shouldBeTypeOf<Request.HwKeyProofAndAccountSignature>()
        (request as Request.HwKeyProofAndAccountSignature).onSuccess(
          "",
          HwAuthSecp256k1PublicKeyMock,
          HwFactorProofOfPossession("")
        )
      }

      awaitScreenWithBody<LoadingBodyModel> {
        this.id.shouldBe(AuthEventTrackerScreenId.ROTATING_AUTH_AFTER_CLOUD_RESTORE)
        authKeyRotationManager.rotateAuthKeysCalls.awaitItem()
        authKeyRotationManager.model.value = AuthKeyRotationRequestState.FinishedRotation(KeyboxMock) {}
        authKeyRotationManager.rotateAuthKeysCalls.awaitItem()
      }

      awaitScreenWithBody<FormBodyModel> {
        this.id.shouldBe(AuthEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH_AFTER_CLOUD_RESTORE)
      }
    }
  }

  test("deactivate other devices -- failure") {
    stateMachine.test(props) {
      // Kick Other People Out
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull()
        secondaryButton.shouldNotBeNull().onClick()
      }

      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps>(
        id = "hw-proof-of-possession"
      ) {
        request.shouldBeTypeOf<Request.HwKeyProofAndAccountSignature>()
        (request as Request.HwKeyProofAndAccountSignature).onSuccess(
          "",
          HwAuthSecp256k1PublicKeyMock,
          HwFactorProofOfPossession("")
        )
      }

      awaitScreenWithBody<LoadingBodyModel> {
        this.id.shouldBe(AuthEventTrackerScreenId.ROTATING_AUTH_AFTER_CLOUD_RESTORE)
        authKeyRotationManager.rotateAuthKeysCalls.awaitItem()
        authKeyRotationManager.model.value = AuthKeyRotationRequestState.FailedRotation {}
        authKeyRotationManager.rotateAuthKeysCalls.awaitItem()
      }

      awaitScreenWithBody<FormBodyModel> {
        this.id.shouldBe(AuthEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_AFTER_CLOUD_BACKUP)
      }
    }
  }

  test("don't deactivate other devices") {
    stateMachine.test(props) {
      // Don't kick Other People Out
      awaitScreenWithBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull()
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitScreenWithBody<LoadingBodyModel> {
        this.id.shouldBe(AuthEventTrackerScreenId.SETTING_ACTIVE_KEYBOX_AFTER_CLOUD_RESTORE)
      }

      onKeyboxRotatedCalls.awaitItem()
    }
  }
})
