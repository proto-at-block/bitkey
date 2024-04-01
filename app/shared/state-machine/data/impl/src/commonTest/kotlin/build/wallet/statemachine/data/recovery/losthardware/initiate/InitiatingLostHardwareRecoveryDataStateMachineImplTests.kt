package build.wallet.statemachine.data.recovery.losthardware.initiate

import build.wallet.bitcoin.recovery.LostHardwareRecoveryStarterMock
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.SpecificClientErrorMock
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryServiceMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.FailedInitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.GeneratingNewAppKeysData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.InitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.VerifyingNotificationCommsData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataProps
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachine
import build.wallet.time.ControlledDelayer
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString.Companion.encodeUtf8

class InitiatingLostHardwareRecoveryDataStateMachineImplTests : FunSpec({

  val appKeysGenerator = AppKeysGeneratorMock()
  val lostHardwareRecoveryStarter =
    LostHardwareRecoveryStarterMock(
      turbine = turbines::create
    )

  val recoveryNotificationVerificationDataStateMachine =
    object : RecoveryNotificationVerificationDataStateMachine, StateMachineMock<RecoveryNotificationVerificationDataProps, RecoveryNotificationVerificationData>(
      initialModel = RecoveryNotificationVerificationData.LoadingNotificationTouchpointData
    ) {}

  val cancelDelayNotifyRecoveryService = CancelDelayNotifyRecoveryServiceMock(turbines::create)

  val sealedCsekMock = "sealedCsek".encodeUtf8()

  val stateMachine =
    InitiatingLostHardwareRecoveryDataStateMachineImpl(
      appKeysGenerator = appKeysGenerator,
      delayer = ControlledDelayer(),
      lostHardwareRecoveryStarter = lostHardwareRecoveryStarter,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      cancelDelayNotifyRecoveryService = cancelDelayNotifyRecoveryService
    )

  beforeTest {
    appKeysGenerator.reset()
    lostHardwareRecoveryStarter.reset()
    cancelDelayNotifyRecoveryService.reset()
  }

  test("initiating lost hardware recovery -- success") {
    stateMachine.test(props = InitiatingLostHardwareRecoveryProps(account = FullAccountMock)) {
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

    stateMachine.test(props = InitiatingLostHardwareRecoveryProps(account = FullAccountMock)) {
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

    stateMachine.test(props = InitiatingLostHardwareRecoveryProps(account = FullAccountMock)) {
      awaitItem().shouldBeTypeOf<GeneratingNewAppKeysData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingNewHardwareData>()
        it.addHardwareKeys(sealedCsekMock, HwKeyBundleMock, AppGlobalAuthKeyHwSignatureMock)
      }

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()

      lostHardwareRecoveryStarter.initiateCalls.awaitItem()

      awaitItem().shouldBeTypeOf<VerifyingNotificationCommsData>()

      lostHardwareRecoveryStarter.initiateResult = Ok(Unit)
      recoveryNotificationVerificationDataStateMachine.props.onComplete()

      awaitItem().shouldBeTypeOf<InitiatingRecoveryWithF8eData>()

      lostHardwareRecoveryStarter.initiateCalls.awaitItem()
    }
  }
})
