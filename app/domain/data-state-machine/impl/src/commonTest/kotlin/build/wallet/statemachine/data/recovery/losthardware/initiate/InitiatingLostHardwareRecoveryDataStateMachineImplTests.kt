package build.wallet.statemachine.data.recovery.losthardware.initiate

import bitkey.recovery.InitiateDelayNotifyRecoveryError.CommsVerificationRequiredError
import bitkey.recovery.InitiateDelayNotifyRecoveryError.OtherError
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.recovery.LostHardwareRecoveryServiceFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.*
import build.wallet.time.MinimumLoadingDuration
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class InitiatingLostHardwareRecoveryDataStateMachineImplTests : FunSpec({

  val appKeysGenerator = AppKeysGeneratorMock()
  val lostHardwareRecoveryService = LostHardwareRecoveryServiceFake()

  val cancelDelayNotifyRecoveryF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)

  val stateMachine =
    InitiatingLostHardwareRecoveryDataStateMachineImpl(
      appKeysGenerator = appKeysGenerator,
      lostHardwareRecoveryService = lostHardwareRecoveryService,
      cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
      minimumLoadingDuration = MinimumLoadingDuration(0.seconds)
    )

  beforeTest {
    appKeysGenerator.reset()
    lostHardwareRecoveryService.reset()
    cancelDelayNotifyRecoveryF8eClient.reset()
  }

  test("initiating lost hardware recovery -- success") {
    stateMachine.test(props = InitiatingLostHardwareRecoveryProps(account = FullAccountMock)) {
      awaitItem().shouldBeTypeOf<GeneratingNewAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingNewHardwareData>()
        it.addHardwareKeys(HwKeyBundleMock, AppGlobalAuthKeyHwSignatureMock)
      }

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()
    }
  }

  test("initiating lost hardware recovery -- failure") {
    lostHardwareRecoveryService.initiateResult = Err(OtherError(Error()))

    stateMachine.test(props = InitiatingLostHardwareRecoveryProps(account = FullAccountMock)) {
      awaitItem().shouldBeTypeOf<GeneratingNewAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingNewHardwareData>()
        it.addHardwareKeys(HwKeyBundleMock, AppGlobalAuthKeyHwSignatureMock)
      }

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()

      awaitItem().shouldBeTypeOf<FailedInitiatingRecoveryWithF8eData>()
    }
  }

  test("initiating lost hardware recovery -- needs comms verification") {
    lostHardwareRecoveryService.initiateResult = Err(CommsVerificationRequiredError(Error()))

    stateMachine.test(props = InitiatingLostHardwareRecoveryProps(account = FullAccountMock)) {
      awaitItem().shouldBeTypeOf<GeneratingNewAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingNewHardwareData>()
        it.addHardwareKeys(HwKeyBundleMock, AppGlobalAuthKeyHwSignatureMock)
      }

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()

      with(awaitItem()) {
        shouldBeTypeOf<VerifyingNotificationCommsData>()
        lostHardwareRecoveryService.initiateResult = Ok(Unit)
        onComplete()
      }

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()
    }
  }
})
