package build.wallet.statemachine.account.create.full.keybox.create

import app.cash.turbine.plusAssign
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.onboarding.CreateFullAccountContext.NewFullAccount
import build.wallet.onboarding.CreateFullAccountServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString

class CreateKeyboxUiStateMachineImplTests : FunSpec({

  val createFullAccountService = CreateFullAccountServiceFake()

  val stateMachine = CreateKeyboxUiStateMachineImpl(
    createFullAccountService = createFullAccountService,
    pairNewHardwareUiStateMachine = object : PairNewHardwareUiStateMachine,
      ScreenStateMachineMock<PairNewHardwareProps>(
        id = "hw-onboard"
      ) {}
  )

  val onExitCalls = turbines.create<Unit>("onExit calls")

  val props = CreateKeyboxUiProps(
    context = NewFullAccount,
    onExit = { onExitCalls += Unit }
  )

  beforeTest {
    createFullAccountService.reset()
  }

  test("happy path") {
    stateMachine.test(props) {
      // creating app keys
      awaitScreenWithBodyModelMock<PairNewHardwareProps> {
        request.shouldBeTypeOf<PairNewHardwareProps.Request.Preparing>()
      }

      // Pairing with HW
      awaitScreenWithBodyModelMock<PairNewHardwareProps>("hw-onboard") {
        val ready = request.shouldBeTypeOf<PairNewHardwareProps.Request.Ready>()
        ready.onSuccess(
          FingerprintEnrolled(
            appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
            keyBundle = HwKeyBundleMock,
            sealedCsek = ByteString.EMPTY,
            serial = "123"
          )
        )
      }

      // Creating account with f8e
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }
})
