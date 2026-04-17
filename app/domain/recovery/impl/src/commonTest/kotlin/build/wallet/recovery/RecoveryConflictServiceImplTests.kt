package build.wallet.recovery

import bitkey.account.AccountConfigServiceFake
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import bitkey.privilegedactions.ActionProofServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoveryConflictServiceError.CommsVerificationRequired
import build.wallet.recovery.RecoveryConflictServiceError.LocalCancelRecoveryConflictError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.getError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.types.shouldBeTypeOf

class RecoveryConflictServiceImplTests : FunSpec({
  val cancelDelayNotifyRecoveryF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)
  val recoveryStatusService = RecoveryStatusServiceMock(NoActiveRecovery, turbines::create)
  val accountConfigService = AccountConfigServiceFake()
  val accountService = AccountServiceFake()
  val actionProofService = ActionProofServiceFake()
  val recoveryConflictService = RecoveryConflictServiceImpl(
    cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
    recoveryStatusService = recoveryStatusService,
    accountConfigService = accountConfigService,
    accountService = accountService,
    actionProofService = actionProofService,
    authTokensService = AuthTokensServiceFake()
  )

  beforeTest {
    cancelDelayNotifyRecoveryF8eClient.reset()
    recoveryStatusService.reset()
    accountConfigService.reset()
    actionProofService.reset()
  }

  test("cancel success clears local recovery status") {
    val result = recoveryConflictService.cancelRecoveryConflict(
      fullAccountId = FullAccountId("full-account-id")
    )
    result.isOk.shouldBeTrue()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
    recoveryStatusService.clearCalls.awaitItem()
  }

  test("maps comms verification required as service error") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(SpecificClientErrorMock(COMMS_VERIFICATION_REQUIRED))

    val result = recoveryConflictService.cancelRecoveryConflict(
      fullAccountId = FullAccountId("full-account-id")
    )
    result.isErr.shouldBeTrue()

    result.getError()
      .shouldBeTypeOf<CommsVerificationRequired>()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("treats no recovery exists as success and clears local recovery status") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(SpecificClientErrorMock(NO_RECOVERY_EXISTS))

    val result = recoveryConflictService.cancelRecoveryConflict(
      fullAccountId = FullAccountId("full-account-id")
    )
    result.isOk.shouldBeTrue()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
    recoveryStatusService.clearCalls.awaitItem()
  }

  test("maps local clear failure as service error") {
    recoveryStatusService.clearCallResult = Err(Error("failed to clear"))

    val result = recoveryConflictService.cancelRecoveryConflict(
      fullAccountId = FullAccountId("full-account-id")
    )
    result.isErr.shouldBeTrue()

    result.getError()
      .shouldBeTypeOf<LocalCancelRecoveryConflictError>()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
    recoveryStatusService.clearCalls.awaitItem()
  }
})
