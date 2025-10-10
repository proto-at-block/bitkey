package build.wallet.recovery

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.db.DbQueryError
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClientFake
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError.ServerError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode.Companion.InternalServerError

class LostHardwareRecoveryServiceImplTests : FunSpec({

  val cancelDelayNotifyRecoveryF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)
  val recoveryStatusService = RecoveryStatusServiceMock(
    StillRecoveringInitiatedRecoveryMock,
    turbines::create
  )
  val accountService = AccountServiceFake()
  val initiateAccountDelayNotifyF8eClient = InitiateAccountDelayNotifyF8eClientFake()
  val sqlDriver = inMemorySqlDriver()
  val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
  val recoveryDao = RecoveryDaoImpl(databaseProvider)
  val service = LostHardwareRecoveryServiceImpl(
    cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
    recoveryStatusService = recoveryStatusService,
    recoveryLock = RecoveryLockImpl(),
    initiateAccountDelayNotifyF8eClient = initiateAccountDelayNotifyF8eClient,
    recoveryDao = recoveryDao,
    accountService = accountService,
    appKeysGenerator = AppKeysGeneratorMock()
  )

  beforeTest {
    cancelDelayNotifyRecoveryF8eClient.reset()
    recoveryStatusService.reset()
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    recoveryDao.clear()
  }

  test("success") {
    service.cancelRecovery().shouldBeOkOfType<Unit>()

    recoveryStatusService.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("success - ignore general 400") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(SpecificClientErrorMock(NO_RECOVERY_EXISTS))

    service.cancelRecovery().shouldBeOk()

    recoveryStatusService.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - backend") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(F8eError.ServerError(ServerError(HttpResponseMock(InternalServerError))))

    service.cancelRecovery().shouldBeErrOfType<F8eCancelDelayNotifyError>()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - dao") {
    recoveryStatusService.clearCallResult = Err(DbQueryError(IllegalStateException()))

    service.cancelRecovery().shouldBeErrOfType<LocalCancelDelayNotifyError>()

    recoveryStatusService.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }
})
