package build.wallet.recovery

import bitkey.account.HardwareType
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import bitkey.privilegedactions.ActionProofError
import bitkey.privilegedactions.ActionProofServiceFake
import bitkey.recovery.InitiateDelayNotifyRecoveryError.OtherError
import build.wallet.account.AccountServiceFake
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbQueryError
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClientFake
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError.ServerError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import build.wallet.recovery.LocalRecoveryAttemptProgress.CreatedPendingKeybundles
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import bitkey.auth.AuthTokenScope
import build.wallet.auth.AccountAuthTokensMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import uniffi.actionproof.Action

class LostHardwareRecoveryServiceImplTests : FunSpec({

  val cancelDelayNotifyRecoveryF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)
  val accountService = AccountServiceFake()
  val initiateAccountDelayNotifyF8eClient = InitiateAccountDelayNotifyF8eClientFake()
  val recoveryDao = RecoveryDaoMock(turbines::create)
  val actionProofService = ActionProofServiceFake()
  val authTokensService = build.wallet.auth.AuthTokensServiceFake()
  val service = LostHardwareRecoveryServiceImpl(
    cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
    recoveryLock = RecoveryLockImpl(),
    initiateAccountDelayNotifyF8eClient = initiateAccountDelayNotifyF8eClient,
    recoveryDao = recoveryDao,
    accountService = accountService,
    appKeysGenerator = AppKeysGeneratorMock(),
    actionProofService = actionProofService,
    authTokensService = authTokensService
  )

  beforeTest {
    cancelDelayNotifyRecoveryF8eClient.reset()
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    recoveryDao.reset()
    actionProofService.reset()
    authTokensService.reset()
    authTokensService.setTokens(FullAccountIdMock, AccountAuthTokensMock, AuthTokenScope.Global)
  }

  test("success") {
    service.cancelRecovery().shouldBeOkOfType<Unit>()

    recoveryDao.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("success - ignore general 400") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(SpecificClientErrorMock(NO_RECOVERY_EXISTS))

    service.cancelRecovery().shouldBeOk()

    recoveryDao.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - backend") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(F8eError.ServerError(ServerError(HttpResponseMock(InternalServerError))))

    service.cancelRecovery().shouldBeErrOfType<F8eCancelDelayNotifyError>()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - dao") {
    recoveryDao.clearCallResult = Err(DbQueryError(IllegalStateException()))

    service.cancelRecovery().shouldBeErrOfType<LocalCancelDelayNotifyError>()

    recoveryDao.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("initiate persists originalAppGlobalAuthKey from account keybox") {
    val result = service.initiate(
      destinationAppKeyBundle = AppKeyBundleMock,
      destinationHardwareKeyBundle = HwKeyBundleMock,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      hardwareType = HardwareType.W1
    )

    result.shouldBeOk()

    // Verify the originalAppGlobalAuthKey was captured from the account's keybox
    val progressCall = recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
    progressCall.shouldBeTypeOf<CreatedPendingKeybundles>().originalAppGlobalAuthKey
      .shouldBe(FullAccountMock.keybox.activeAppKeyBundle.authKey)

    // Consume the setActiveServerRecovery call
    recoveryDao.setActiveServerRecoveryCalls.awaitItem()
  }

  test("cancelRecovery does not build action proof for W1 account") {
    accountService.setActiveAccount(FullAccountMock)

    service.cancelRecovery().shouldBeOk()

    // No action proof calls should have been made
    actionProofService.createAppSignedHeaderCalls.shouldBe(emptyList())

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
    recoveryDao.clearCalls.awaitItem()
  }

  test("cancelRecovery builds app-signed action proof for W3 account") {
    accountService.setActiveAccount(FullAccountW3Mock)

    service.cancelRecovery().shouldBeOk()

    // Verify action proof was built with correct action
    actionProofService.createAppSignedHeaderCalls.size.shouldBe(1)
    actionProofService.createAppSignedHeaderCalls.first().action
      .shouldBe(Action.CANCEL_LOST_HARDWARE_RECOVERY)

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
    recoveryDao.clearCalls.awaitItem()
  }

  test("cancelRecovery fails when action proof creation fails for W3 account") {
    accountService.setActiveAccount(FullAccountW3Mock)
    actionProofService.createAppSignedHeaderResult =
      Err(ActionProofError.InternalError(RuntimeException("signing failed")))

    service.cancelRecovery().shouldBeErrOfType<LocalCancelDelayNotifyError>()
  }

  test("initiate fails when action proof creation fails for W3 account") {
    accountService.setActiveAccount(FullAccountW3Mock)
    actionProofService.createAppSignedHeaderResult =
      Err(ActionProofError.InternalError(RuntimeException("signing failed")))

    service.initiate(
      destinationAppKeyBundle = AppKeyBundleMock,
      destinationHardwareKeyBundle = HwKeyBundleMock,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      hardwareType = HardwareType.W3
    ).shouldBeErrOfType<OtherError>()

    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
  }

  test("initiate does not build action proof for W1 account") {
    accountService.setActiveAccount(FullAccountMock)

    service.initiate(
      destinationAppKeyBundle = AppKeyBundleMock,
      destinationHardwareKeyBundle = HwKeyBundleMock,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      hardwareType = HardwareType.W1
    ).shouldBeOk()

    // No action proof calls should have been made
    actionProofService.createAppSignedHeaderCalls.shouldBe(emptyList())

    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
    recoveryDao.setActiveServerRecoveryCalls.awaitItem()
  }

  test("initiate builds app-signed action proof for W3 account") {
    accountService.setActiveAccount(FullAccountW3Mock)

    service.initiate(
      destinationAppKeyBundle = AppKeyBundleMock,
      destinationHardwareKeyBundle = HwKeyBundleMock,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      hardwareType = HardwareType.W3
    ).shouldBeOk()

    // Verify action proof was built with correct action
    actionProofService.createAppSignedHeaderCalls.size.shouldBe(1)
    actionProofService.createAppSignedHeaderCalls.first().action
      .shouldBe(Action.CREATE_LOST_HARDWARE_RECOVERY)

    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
    recoveryDao.setActiveServerRecoveryCalls.awaitItem()
  }
})
