package build.wallet.statemachine.data.recovery.lostapp

import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.SpecificClientErrorMock
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryServiceMock
import build.wallet.f8e.recovery.InitiateHardwareAuthServiceMock
import build.wallet.f8e.recovery.ListKeysetsServiceMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError.F8eAccountAuthenticationFailed
import build.wallet.recovery.LostAppRecoveryAuthenticatorMock
import build.wallet.recovery.LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.F8eInitiateDelayNotifyError
import build.wallet.recovery.LostAppRecoveryInitiatorMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AuthenticatingWithF8EViaAppData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AwaitingAppSignedAuthChallengeData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AwaitingHardwareProofOfPossessionAndKeysData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AwaitingHwKeysData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.AwaitingPushNotificationPermissionData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.CancellingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.DisplayingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.FailedToAuthenticateWithF8EViaAppData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.FailedToInitiateAppAuthWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.FailedToInitiateLostAppWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.InitiatingAppAuthWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.InitiatingLostAppRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.ListingKeysetsFromF8eData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.VerifyingNotificationCommsData
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryProps
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataProps
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachine
import build.wallet.time.ControlledDelayer
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf

class InitiatingLostAppRecoveryDataStateMachineImplTests : FunSpec({

  val keyBundleMock = HwKeyBundleMock
  val fullAccountConfig = FullAccountConfigMock
  val appKeysGenerator = AppKeysGeneratorMock()
  val initiateHardwareAuthService = InitiateHardwareAuthServiceMock(turbines::create)
  val lostAppRecoveryInitiator = LostAppRecoveryInitiatorMock(turbines::create)
  val lostAppRecoveryAuthenticator = LostAppRecoveryAuthenticatorMock(turbines::create)
  val cancelDelayNotifyService = CancelDelayNotifyRecoveryServiceMock(turbines::create)
  val recoveryNotificationVerificationDataStateMachine =
    object : RecoveryNotificationVerificationDataStateMachine,
      StateMachineMock<RecoveryNotificationVerificationDataProps, RecoveryNotificationVerificationData>(
        initialModel = RecoveryNotificationVerificationData.LoadingNotificationTouchpointData
      ) {}
  val uuid = UuidGeneratorFake()

  val stateMachine =
    InitiatingLostAppRecoveryDataStateMachineImpl(
      appKeysGenerator = appKeysGenerator,
      initiateHardwareAuthService = initiateHardwareAuthService,
      listKeysetsService = ListKeysetsServiceMock(),
      lostAppRecoveryInitiator = lostAppRecoveryInitiator,
      lostAppRecoveryAuthenticator = lostAppRecoveryAuthenticator,
      recoveryNotificationVerificationDataStateMachine = recoveryNotificationVerificationDataStateMachine,
      cancelDelayNotifyRecoveryService = cancelDelayNotifyService,
      delayer = ControlledDelayer(),
      uuidGenerator = uuid
    )

  beforeTest {
    lostAppRecoveryInitiator.reset()
    initiateHardwareAuthService.reset()
    lostAppRecoveryAuthenticator.reset()
    cancelDelayNotifyService.reset()
  }

  test("initiating lost app recovery -- success") {
    stateMachine.test(
      props =
        InitiatingLostAppRecoveryProps(
          fullAccountConfig = fullAccountConfig,
          onRollback = { }
        )
    ) {
      getHardwareKeys(keyBundleMock)
      signChallenge()
      listKeysets()
      getProofOfPossession()
      enablePush()

      initiateHardwareAuthService.startCalls.awaitItem()
      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()
    }
  }

  test("initiating lost app recovery -- auth initiation service initiation failure/retry") {
    stateMachine.test(
      props =
        InitiatingLostAppRecoveryProps(
          fullAccountConfig = fullAccountConfig,
          onRollback = {}
        )
    ) {

      initiateHardwareAuthService.challengeResult = Err(NetworkError(Error()))

      getHardwareKeys(keyBundleMock)

      awaitItem().let {
        it.shouldBeTypeOf<FailedToInitiateAppAuthWithF8eData>()
        initiateHardwareAuthService.reset()
        it.retry()
        awaitItem().shouldBeTypeOf<InitiatingAppAuthWithF8eData>()
        awaitItem().shouldBeTypeOf<AwaitingAppSignedAuthChallengeData>()
      }

      // once for failure, once for success
      initiateHardwareAuthService.startCalls.awaitItem()
      initiateHardwareAuthService.startCalls.awaitItem()
    }
  }

  test("initiating lost app recovery -- authenticator service failure") {

    stateMachine.test(
      props =
        InitiatingLostAppRecoveryProps(
          fullAccountConfig = fullAccountConfig,
          onRollback = {}
        )
    ) {
      lostAppRecoveryAuthenticator.authenticationResult =
        Err(F8eAccountAuthenticationFailed(Error()))

      getHardwareKeys(keyBundleMock)
      signChallenge()

      awaitItem().let {
        it.shouldBeTypeOf<FailedToAuthenticateWithF8EViaAppData>()
        lostAppRecoveryAuthenticator.reset()
        it.retry()
        awaitItem().shouldBeTypeOf<AuthenticatingWithF8EViaAppData>()
        awaitItem().shouldBeTypeOf<ListingKeysetsFromF8eData>()
        awaitItem().shouldBeTypeOf<AwaitingHardwareProofOfPossessionAndKeysData>()
      }
    }
    initiateHardwareAuthService.startCalls.awaitItem()

    // await one for the failure and one for the success
    lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
    lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
  }

  test("initiating lost app recovery -- initiate recovery failure/retry") {
    stateMachine.test(
      props =
        InitiatingLostAppRecoveryProps(
          fullAccountConfig = fullAccountConfig,
          onRollback = {}
        )
    ) {
      lostAppRecoveryInitiator.recoveryResult =
        Err(
          F8eInitiateDelayNotifyError(
            F8eError.UnhandledException(HttpError.UnhandledException(Throwable()))
          )
        )

      getHardwareKeys(keyBundleMock)
      signChallenge()
      listKeysets()
      getProofOfPossession()
      enablePush()

      awaitItem().let {
        it.shouldBeTypeOf<FailedToInitiateLostAppWithF8eData>()
        it.retry()
        awaitItem().shouldBeTypeOf<InitiatingLostAppRecoveryWithF8eData>()
        awaitItem().shouldBeTypeOf<FailedToInitiateLostAppWithF8eData>()
      }
    }

    initiateHardwareAuthService.startCalls.awaitItem()
    lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
    lostAppRecoveryInitiator.initiateCalls.awaitItem()
    lostAppRecoveryInitiator.initiateCalls.awaitItem()
  }

  test("initiating lost app recovery -- requires comms verification -- success") {
    stateMachine.test(
      props =
        InitiatingLostAppRecoveryProps(
          fullAccountConfig = fullAccountConfig,
          onRollback = { }
        )
    ) {
      lostAppRecoveryInitiator.recoveryResult =
        Err(
          F8eInitiateDelayNotifyError(
            SpecificClientErrorMock(InitiateAccountDelayNotifyErrorCode.COMMS_VERIFICATION_REQUIRED)
          )
        )

      getHardwareKeys(keyBundleMock)
      signChallenge()
      listKeysets()
      getProofOfPossession()
      enablePush()

      initiateHardwareAuthService.startCalls.awaitItem()
      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()

      awaitItem().shouldBeTypeOf<VerifyingNotificationCommsData>()

      lostAppRecoveryInitiator.recoveryResult = Ok(Unit)
      recoveryNotificationVerificationDataStateMachine.props.onComplete()

      awaitItem().shouldBeTypeOf<InitiatingLostAppRecoveryWithF8eData>()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()
    }
  }

  test("test cancellation") {
    stateMachine.test(
      props =
        InitiatingLostAppRecoveryProps(
          fullAccountConfig = fullAccountConfig,
          onRollback = { }
        )
    ) {
      getHardwareKeys(keyBundleMock)
      signChallenge()
      listKeysets()
      getProofOfPossession()
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingPushNotificationPermissionData>()
        it.onRetreat()
      }
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHwKeysData>()
      }

      initiateHardwareAuthService.startCalls.awaitItem()
      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
    }
  }
  test("initiating lost app recovery -- existing recovery exists -- cancel it -- without comms") {
    stateMachine.test(
      props =
        InitiatingLostAppRecoveryProps(
          fullAccountConfig = fullAccountConfig,
          onRollback = { }
        )
    ) {

      lostAppRecoveryInitiator.recoveryResult =
        Err(
          F8eInitiateDelayNotifyError(
            SpecificClientErrorMock(InitiateAccountDelayNotifyErrorCode.RECOVERY_ALREADY_EXISTS)
          )
        )

      getHardwareKeys(keyBundleMock)
      signChallenge()
      listKeysets()
      getProofOfPossession()
      enablePush()

      initiateHardwareAuthService.startCalls.awaitItem()
      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()

      lostAppRecoveryInitiator.reset()

      awaitItem().let {
        it.shouldBeTypeOf<DisplayingConflictingRecoveryData>()
        it.onCancelRecovery()
      }

      cancelDelayNotifyService.cancelRecoveryCalls.awaitItem()

      awaitItem().shouldBeTypeOf<CancellingConflictingRecoveryData>()
      awaitItem().shouldBeTypeOf<InitiatingLostAppRecoveryWithF8eData>()

      lostAppRecoveryInitiator.initiateCalls.awaitItem()
    }
  }

  test("initiating lost app recovery -- existing recovery exists -- cancel it -- with comms") {
    stateMachine.test(
      props =
        InitiatingLostAppRecoveryProps(
          fullAccountConfig = fullAccountConfig,
          onRollback = { }
        )
    ) {

      lostAppRecoveryInitiator.recoveryResult =
        Err(
          F8eInitiateDelayNotifyError(
            SpecificClientErrorMock(InitiateAccountDelayNotifyErrorCode.RECOVERY_ALREADY_EXISTS)
          )
        )

      cancelDelayNotifyService.cancelResult =
        Err(
          SpecificClientErrorMock(CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED)
        )

      getHardwareKeys(keyBundleMock)
      signChallenge()
      listKeysets()
      getProofOfPossession()
      enablePush()

      initiateHardwareAuthService.startCalls.awaitItem()
      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()

      lostAppRecoveryInitiator.recoveryResult = Ok(Unit)

      awaitItem().let {
        it.shouldBeTypeOf<DisplayingConflictingRecoveryData>()
        it.onCancelRecovery()
      }

      cancelDelayNotifyService.cancelRecoveryCalls.awaitItem()
      awaitItem().shouldBeTypeOf<CancellingConflictingRecoveryData>()
      awaitItem().shouldBeTypeOf<VerifyingNotificationCommsData>()
    }
  }
})

private suspend fun StateMachineTester<InitiatingLostAppRecoveryProps, InitiatingLostAppRecoveryData>.getHardwareKeys(
  keybundleMock: HwKeyBundle,
) {
  awaitItem().let {
    it.shouldBeTypeOf<AwaitingHwKeysData>()
    it.addHardwareAuthKey(keybundleMock.authKey)
  }
  awaitItem().shouldBeTypeOf<InitiatingAppAuthWithF8eData>()
}

private suspend fun StateMachineTester<InitiatingLostAppRecoveryProps, InitiatingLostAppRecoveryData>.signChallenge() {
  awaitItem().let {
    it.shouldBeTypeOf<AwaitingAppSignedAuthChallengeData>()
    it.addSignedChallenge("signed-challenge")
  }

  awaitItem().shouldBeTypeOf<AuthenticatingWithF8EViaAppData>()
}

private suspend fun StateMachineTester<InitiatingLostAppRecoveryProps, InitiatingLostAppRecoveryData>.listKeysets() {
  awaitItem().let {
    it.shouldBeTypeOf<ListingKeysetsFromF8eData>()
  }
}

private suspend fun StateMachineTester<InitiatingLostAppRecoveryProps, InitiatingLostAppRecoveryData>.getProofOfPossession() {
  awaitItem().let {
    it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionAndKeysData>()
    it.onComplete(
      HwFactorProofOfPossession("mock-hw-proof-of-possession"),
      HwKeyBundleMock.spendingKey,
      AppGlobalAuthKeyHwSignatureMock
    )
  }
}

private suspend fun StateMachineTester<InitiatingLostAppRecoveryProps, InitiatingLostAppRecoveryData>.enablePush() {
  awaitItem().let {
    it.shouldBeTypeOf<AwaitingPushNotificationPermissionData>()
    it.onComplete()
  }
  awaitItem().shouldBeTypeOf<InitiatingLostAppRecoveryWithF8eData>()
}
