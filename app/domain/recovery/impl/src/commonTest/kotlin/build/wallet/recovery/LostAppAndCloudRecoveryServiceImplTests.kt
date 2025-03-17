package build.wallet.recovery

import bitkey.account.AccountConfigServiceFake
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.f8e.auth.AuthF8eClientMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError.ServerError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode.Companion.InternalServerError

class LostAppAndCloudRecoveryServiceImplTests : FunSpec({

  val cancelDelayNotifyRecoveryF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)
  val authF8eClient = AuthF8eClientMock()
  val recoverySyncer = RecoverySyncerMock(
    StillRecoveringInitiatedRecoveryMock,
    turbines::create
  )
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val authTokensService = AuthTokensServiceFake()
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val appKeysGenerator = AppKeysGeneratorMock()
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val accountConfigService = AccountConfigServiceFake()
  val service = LostAppAndCloudRecoveryServiceImpl(
    authF8eClient = authF8eClient,
    cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
    recoverySyncer = recoverySyncer,
    recoveryLock = RecoveryLock(),
    accountConfigService = accountConfigService,
    accountAuthenticator = accountAuthenticator,
    authTokensService = authTokensService,
    deviceTokenManager = deviceTokenManager,
    appKeysGenerator = appKeysGenerator,
    listKeysetsF8eClient = listKeysetsF8eClient
  )

  suspend fun LostAppAndCloudRecoveryService.cancel() =
    cancelRecovery(
      accountId = FullAccountIdMock,
      hwProofOfPossession = HwFactorProofOfPossession("")
    )

  beforeTest {
    cancelDelayNotifyRecoveryF8eClient.reset()
    recoverySyncer.reset()
    accountConfigService.reset()
    authF8eClient.reset()
    accountAuthenticator.reset()
    authTokensService.reset()
    deviceTokenManager.reset()
    listKeysetsF8eClient.reset()
    appKeysGenerator.reset()
  }

  test("success") {
    service.cancel().shouldBeOkOfType<Unit>()

    recoverySyncer.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("success - ignore general 400") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(SpecificClientErrorMock(NO_RECOVERY_EXISTS))

    service.cancel().shouldBeOk()

    recoverySyncer.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - backend") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(F8eError.ServerError(ServerError(HttpResponseMock(InternalServerError))))

    service.cancel().shouldBeErrOfType<F8eCancelDelayNotifyError>()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - dao") {
    recoverySyncer.clearCallResult = Err(DbQueryError(IllegalStateException()))

    service.cancel().shouldBeErrOfType<LocalCancelDelayNotifyError>()

    recoverySyncer.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }
})
