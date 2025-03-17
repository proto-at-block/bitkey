package build.wallet.statemachine.data.recovery.losthardware.initiate

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.bitcoin.recovery.LostHardwareRecoveryStarterMock
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.*
import build.wallet.time.MinimumLoadingDuration
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Duration.Companion.seconds

class InitiatingLostHardwareRecoveryDataStateMachineImplTests : FunSpec({

  val appKeysGenerator = AppKeysGeneratorMock()
  val lostHardwareRecoveryStarter =
    LostHardwareRecoveryStarterMock(
      turbine = turbines::create
    )

  val cancelDelayNotifyRecoveryF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)

  val sealedCsekMock = "sealedCsek".encodeUtf8()

  val stateMachine =
    InitiatingLostHardwareRecoveryDataStateMachineImpl(
      appKeysGenerator = appKeysGenerator,
      lostHardwareRecoveryStarter = lostHardwareRecoveryStarter,
      cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
      minimumLoadingDuration = MinimumLoadingDuration(0.seconds)
    )

  beforeTest {
    appKeysGenerator.reset()
    lostHardwareRecoveryStarter.reset()
    cancelDelayNotifyRecoveryF8eClient.reset()
  }

  test("initiating lost hardware recovery -- success") {
    stateMachine.testWithVirtualTime(props = InitiatingLostHardwareRecoveryProps(account = FullAccountMock)) {
      awaitItem().shouldBeTypeOf<GeneratingNewAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingNewHardwareData>()
        it.addHardwareKeys(sealedCsekMock, HwKeyBundleMock, AppGlobalAuthKeyHwSignatureMock)
      }

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()

      lostHardwareRecoveryStarter.initiateCalls.awaitItem()
    }
  }

  test("initiating lost hardware recovery -- failure") {

    lostHardwareRecoveryStarter.initiateResult =
      Err(
        InitiateDelayNotifyHardwareRecoveryError.F8eInitiateDelayNotifyError(
          error = F8eError.UnhandledException(HttpError.UnhandledException(Throwable()))
        )
      )

    stateMachine.testWithVirtualTime(props = InitiatingLostHardwareRecoveryProps(account = FullAccountMock)) {
      awaitItem().shouldBeTypeOf<GeneratingNewAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingNewHardwareData>()
        it.addHardwareKeys(sealedCsekMock, HwKeyBundleMock, AppGlobalAuthKeyHwSignatureMock)
      }

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()

      awaitItem().shouldBeTypeOf<FailedInitiatingRecoveryWithF8eData>()

      lostHardwareRecoveryStarter.initiateCalls.awaitItem()
    }
  }

  test("initiating lost hardware recovery -- needs comms verification") {
    lostHardwareRecoveryStarter.initiateResult =
      Err(
        InitiateDelayNotifyHardwareRecoveryError.F8eInitiateDelayNotifyError(
          error = SpecificClientErrorMock(InitiateAccountDelayNotifyErrorCode.COMMS_VERIFICATION_REQUIRED)
        )
      )

    stateMachine.testWithVirtualTime(props = InitiatingLostHardwareRecoveryProps(account = FullAccountMock)) {
      awaitItem().shouldBeTypeOf<GeneratingNewAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingNewHardwareData>()
        it.addHardwareKeys(sealedCsekMock, HwKeyBundleMock, AppGlobalAuthKeyHwSignatureMock)
      }

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()

      lostHardwareRecoveryStarter.initiateCalls.awaitItem()

      with(awaitItem()) {
        shouldBeTypeOf<VerifyingNotificationCommsData>()
        lostHardwareRecoveryStarter.initiateResult = Ok(Unit)
        onComplete()
      }

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()

      lostHardwareRecoveryStarter.initiateCalls.awaitItem()
    }
  }
})
