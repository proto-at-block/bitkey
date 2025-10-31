package build.wallet.statemachine.account.create.full.keybox.create

import app.cash.turbine.plusAssign
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.onboarding.CreateFullAccountContext.NewFullAccount
import build.wallet.onboarding.OnboardFullAccountServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString

class CreateKeyboxUiStateMachineImplTests : FunSpec({

  val createFullAccountService = OnboardFullAccountServiceFake()

  val stateMachine = CreateKeyboxUiStateMachineImpl(
    onboardFullAccountService = createFullAccountService,
    pairNewHardwareUiStateMachine = object : PairNewHardwareUiStateMachine,
      ScreenStateMachineMock<PairNewHardwareProps>(
        id = "hw-onboard"
      ) {}
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
  }

  val hwActivation = FingerprintEnrolled(
    appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
    keyBundle = HwKeyBundleMock,
    sealedCsek = ByteString.EMPTY,
    sealedSsek = ByteString.EMPTY,
    serial = "123"
  )

  test("happy path - account creation") {
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

      onAccountCreatedCalls.awaitItem()
    }
  }
})
