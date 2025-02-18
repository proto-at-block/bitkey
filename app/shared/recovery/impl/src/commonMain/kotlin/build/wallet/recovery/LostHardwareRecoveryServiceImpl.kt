package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.error.F8eError.SpecificClientError
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClient
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import kotlinx.coroutines.sync.withLock

@BitkeyInject(AppScope::class)
class LostHardwareRecoveryServiceImpl(
  private val cancelDelayNotifyRecoveryF8eClient: CancelDelayNotifyRecoveryF8eClient,
  private val recoverySyncer: RecoverySyncer,
  private val recoveryLock: RecoveryLock,
) : LostHardwareRecoveryService {
  override suspend fun cancelRecovery(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
  ): Result<Unit, CancelDelayNotifyRecoveryError> =
    coroutineBinding {
      recoveryLock.withLock {
        cancelDelayNotifyRecoveryF8eClient
          .cancel(
            f8eEnvironment = f8eEnvironment,
            fullAccountId = accountId,
            hwFactorProofOfPossession = null
          )
          .recoverIf(
            predicate = { f8eError ->
              // We expect to get a 4xx NO_RECOVERY_EXISTS error if we try to cancel
              // a recovery that has already been canceled. In that case, treat it as
              // a success, so we will still proceed below and delete the stored recovery
              val clientError =
                f8eError as? SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
              clientError?.errorCode == NO_RECOVERY_EXISTS
            },
            transform = {}
          )
          .mapError(::F8eCancelDelayNotifyError)
          .bind()

        recoverySyncer.clear()
          .mapError(::LocalCancelDelayNotifyError)
          .bind()
      }
    }
}
