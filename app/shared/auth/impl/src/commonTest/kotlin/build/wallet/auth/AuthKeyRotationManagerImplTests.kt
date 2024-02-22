package build.wallet.auth

import app.cash.molecule.RecompositionClock
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.RotateAuthKeysServiceMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration.Companion.seconds

class AuthKeyRotationManagerImplTests : FunSpec({

  val authKeyRotationAttemptDao = AuthKeyRotationAttemptDaoMock(turbines::create)
  val appKeysGenerator = AppKeysGeneratorMock(turbines::create)
  val rotateAuthKeysService = RotateAuthKeysServiceMock(turbines::create)
  val keyboxDao = KeyboxDaoMock(turbines::create)

  val authKeyRotationManager = AuthKeyRotationManagerImpl(
    authKeyRotationAttemptDao = authKeyRotationAttemptDao,
    appKeysGenerator = appKeysGenerator,
    rotateAuthKeysService = rotateAuthKeysService,
    keyboxDao = keyboxDao
  )

  val f8eEnvironment = KeyboxMock.config.f8eEnvironment
  val accountId = KeyboxMock.fullAccountId

  beforeTest {
    appKeysGenerator.reset()
    keyboxDao.reset()
  }

  test("rotate auth keys with and rotateActiveKeybox = true") {
    runKeyRotationTest(
      authKeyRotationManager,
      f8eEnvironment,
      accountId,
      authKeyRotationAttemptDao,
      rotateAuthKeysService,
      keyboxDao,
      shouldRotateLocalKeybox = true
    )
  }

  test("rotate auth keys with and rotateActiveKeybox = false") {
    runKeyRotationTest(
      authKeyRotationManager,
      f8eEnvironment,
      accountId,
      authKeyRotationAttemptDao,
      rotateAuthKeysService,
      keyboxDao,
      shouldRotateLocalKeybox = false
    )
  }
})

private suspend fun runKeyRotationTest(
  authKeyRotationManager: AuthKeyRotationManagerImpl,
  f8eEnvironment: F8eEnvironment,
  accountId: FullAccountId,
  authKeyRotationAttemptDao: AuthKeyRotationAttemptDaoMock,
  rotateAuthKeysService: RotateAuthKeysServiceMock,
  keyboxDao: KeyboxDaoMock,
  shouldRotateLocalKeybox: Boolean = true,
) {
  val hwProofOfPossession = HwFactorProofOfPossession("signed-token")
  val keyboxToRotate = KeyboxMock
  val hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock
  val hwSignedAccountId = "signed-account-id"
  val authKeys = AppAuthPublicKeys(
    appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
    appRecoveryAuthPublicKey = AppRecoveryAuthPublicKeyMock
  )

  moleculeFlow(RecompositionClock.Immediate) {
    authKeyRotationManager.startOrResumeAuthKeyRotation(
      hwFactorProofOfPossession = hwProofOfPossession,
      keyboxToRotate = keyboxToRotate,
      rotateActiveKeybox = shouldRotateLocalKeybox,
      hwAuthPublicKey = hwAuthPublicKey,
      hwSignedAccountId = hwSignedAccountId
    )
  }.distinctUntilChanged().test(timeout = 10.seconds) {
    awaitItem().shouldBeTypeOf<AuthKeyRotationRequestState.Rotating>()

    moveThroughAttemptStates(authKeyRotationAttemptDao, authKeys)

    // make sure our dependencies were called as expected
    authKeyRotationAttemptDao.getAuthKeyRotationAttemptStateCalls.awaitItem()
    authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
    rotateAuthKeysService.rotateKeysetCalls.awaitItem()
    authKeyRotationAttemptDao.setServerRotationAttemptCompleteCalls.awaitItem()

    if (shouldRotateLocalKeybox) {
      keyboxDao.rotateAuthKeysCalls.awaitItem()
    }

    awaitItem().shouldBeTypeOf<AuthKeyRotationRequestState.FinishedRotation>().clearAttempt()
    authKeyRotationAttemptDao.clearCalls.awaitItem()

    // make sure no other states were emitted
    expectNoEvents()
  }
}

private suspend fun moveThroughAttemptStates(
  authKeyRotationAttemptDao: AuthKeyRotationAttemptDaoMock,
  authKeys: AppAuthPublicKeys,
) {
  // simulate the NoAttemptInProgress state being emitted from authKeyRotationAttemptDaoState
  authKeyRotationAttemptDao.stateFlow.emit(AuthKeyRotationAttemptDaoState.NoAttemptInProgress)

  // simulate the AuthKeysWritten state being emitted from authKeyRotationAttemptDaoState
  authKeyRotationAttemptDao.stateFlow.emit(
    AuthKeyRotationAttemptDaoState.AuthKeysWritten(
      appAuthPublicKeys = authKeys,
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock
    )
  )

  // simulate the ServerRotationAttemptComplete state being emitted from authKeyRotationAttemptDaoState
  authKeyRotationAttemptDao.stateFlow.emit(
    AuthKeyRotationAttemptDaoState.ServerRotationAttemptComplete(
      appAuthPublicKeys = authKeys
    )
  )
}
