package build.wallet.statemachine.data.recovery.lostapp

import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.AuthF8eClientMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.InitiateAuthenticationSuccessMock
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.SpecificClientErrorMock
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.LostAppRecoveryAuthenticator.DelayNotifyLostAppAuthError.F8eAccountAuthenticationFailed
import build.wallet.recovery.LostAppRecoveryAuthenticatorMock
import build.wallet.recovery.LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.F8eInitiateDelayNotifyError
import build.wallet.recovery.LostAppRecoveryInitiatorMock
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.*
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryProps
import build.wallet.time.MinimumLoadingDuration
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class InitiatingLostAppRecoveryDataStateMachineImplTests : FunSpec({

  val keyBundleMock = HwKeyBundleMock
  val fullAccountConfig = FullAccountConfigMock
  val appKeysGenerator = AppKeysGeneratorMock()
  val authF8eClient = AuthF8eClientMock(
    defaultInitiateAuthenticationResult = Ok(InitiateAuthenticationSuccessMock)
  )
  val lostAppRecoveryInitiator = LostAppRecoveryInitiatorMock(turbines::create)
  val lostAppRecoveryAuthenticator = LostAppRecoveryAuthenticatorMock(turbines::create)
  val cancelDelayNotifyF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)
  val uuid = UuidGeneratorFake()

  val stateMachine =
    InitiatingLostAppRecoveryDataStateMachineImpl(
      appKeysGenerator = appKeysGenerator,
      authF8eClient = authF8eClient,
      listKeysetsF8eClient = ListKeysetsF8eClientMock(),
      lostAppRecoveryInitiator = lostAppRecoveryInitiator,
      lostAppRecoveryAuthenticator = lostAppRecoveryAuthenticator,
      cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyF8eClient,
      uuidGenerator = uuid,
      minimumLoadingDuration = MinimumLoadingDuration(0.seconds)
    )

  beforeTest {
    lostAppRecoveryInitiator.reset()
    authF8eClient.reset()
    lostAppRecoveryAuthenticator.reset()
    cancelDelayNotifyF8eClient.reset()
  }

  test("initiating lost app recovery -- success") {
    stateMachine.testWithVirtualTime(
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

      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()
    }
  }

  test("initiating lost app recovery -- auth initiation service initiation failure/retry") {
    stateMachine.testWithVirtualTime(
      props =
        InitiatingLostAppRecoveryProps(
          fullAccountConfig = fullAccountConfig,
          onRollback = {}
        )
    ) {

      authF8eClient.initiateAuthenticationResult = Err(NetworkError(Error()))

      getHardwareKeys(keyBundleMock)

      awaitItem().let {
        it.shouldBeTypeOf<FailedToInitiateAppAuthWithF8eData>()
        authF8eClient.reset()
        it.retry()
        awaitItem().shouldBeTypeOf<InitiatingAppAuthWithF8eData>()
        awaitItem().shouldBeTypeOf<AwaitingAppSignedAuthChallengeData>()
      }
    }
  }

  test("initiating lost app recovery -- authenticator service failure") {

    stateMachine.testWithVirtualTime(
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

    // await one for the failure and one for the success
    lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
    lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
  }

  test("initiating lost app recovery -- initiate recovery failure/retry") {
    stateMachine.testWithVirtualTime(
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

    lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
    lostAppRecoveryInitiator.initiateCalls.awaitItem()
    lostAppRecoveryInitiator.initiateCalls.awaitItem()
  }

  test("initiating lost app recovery -- requires comms verification -- success") {
    stateMachine.testWithVirtualTime(
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

      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()

      with(awaitItem()) {
        shouldBeTypeOf<VerifyingNotificationCommsData>()
        lostAppRecoveryInitiator.recoveryResult = Ok(Unit)
        onComplete()
      }

      awaitItem().shouldBeTypeOf<InitiatingLostAppRecoveryWithF8eData>()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()
    }
  }

  test("test cancellation") {
    stateMachine.testWithVirtualTime(
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

      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
    }
  }
  test("initiating lost app recovery -- existing recovery exists -- cancel it -- without comms") {
    stateMachine.testWithVirtualTime(
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

      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()

      lostAppRecoveryInitiator.reset()

      awaitItem().let {
        it.shouldBeTypeOf<DisplayingConflictingRecoveryData>()
        it.onCancelRecovery()
      }

      cancelDelayNotifyF8eClient.cancelRecoveryCalls.awaitItem()

      awaitItem().shouldBeTypeOf<CancellingConflictingRecoveryData>()
      awaitItem().shouldBeTypeOf<InitiatingLostAppRecoveryWithF8eData>()

      lostAppRecoveryInitiator.initiateCalls.awaitItem()
    }
  }

  test("initiating lost app recovery -- existing recovery exists -- cancel it -- with comms") {
    stateMachine.testWithVirtualTime(
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

      cancelDelayNotifyF8eClient.cancelResult =
        Err(
          SpecificClientErrorMock(CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED)
        )

      getHardwareKeys(keyBundleMock)
      signChallenge()
      listKeysets()
      getProofOfPossession()
      enablePush()

      lostAppRecoveryAuthenticator.authenticateCalls.awaitItem()
      lostAppRecoveryInitiator.initiateCalls.awaitItem()

      lostAppRecoveryInitiator.recoveryResult = Ok(Unit)

      awaitItem().let {
        it.shouldBeTypeOf<DisplayingConflictingRecoveryData>()
        it.onCancelRecovery()
      }

      cancelDelayNotifyF8eClient.cancelRecoveryCalls.awaitItem()
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
