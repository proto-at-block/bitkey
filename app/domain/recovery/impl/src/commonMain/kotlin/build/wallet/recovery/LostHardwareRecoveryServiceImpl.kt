package build.wallet.recovery

import bitkey.f8e.error.F8eError.SpecificClientError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode.COMMS_VERIFICATION_REQUIRED
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode.RECOVERY_ALREADY_EXISTS
import bitkey.recovery.InitiateDelayNotifyRecoveryError
import bitkey.recovery.InitiateDelayNotifyRecoveryError.*
import bitkey.recovery.RecoveryStatusService
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClient
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClient
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import build.wallet.recovery.LocalRecoveryAttemptProgress.CreatedPendingKeybundles
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import kotlinx.coroutines.sync.withLock

@BitkeyInject(AppScope::class)
class LostHardwareRecoveryServiceImpl(
  private val cancelDelayNotifyRecoveryF8eClient: CancelDelayNotifyRecoveryF8eClient,
  private val recoveryStatusService: RecoveryStatusService,
  private val recoveryLock: RecoveryLock,
  private val initiateAccountDelayNotifyF8eClient: InitiateAccountDelayNotifyF8eClient,
  private val recoveryDao: RecoveryDao,
  private val accountService: AccountService,
) : LostHardwareRecoveryService {
  override suspend fun initiate(
    destinationAppKeyBundle: AppKeyBundle,
    destinationHardwareKeyBundle: HwKeyBundle,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<Unit, InitiateDelayNotifyRecoveryError> =
    coroutineBinding {
      recoveryLock.withLock {
        val account = accountService.getAccount<FullAccount>()
          .mapError(::OtherError)
          .bind()

        // Persist local pending recovery state
        recoveryDao.setLocalRecoveryProgress(
          CreatedPendingKeybundles(
            fullAccountId = account.accountId,
            appKeyBundle = destinationAppKeyBundle,
            hwKeyBundle = destinationHardwareKeyBundle,
            appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
            lostFactor = Hardware
          )
        ).mapError { OtherError(it) }

        // Initiate delay period with f8e
        val serviceResponse =
          initiateAccountDelayNotifyF8eClient.initiate(
            f8eEnvironment = account.config.f8eEnvironment,
            fullAccountId = account.accountId,
            lostFactor = Hardware,
            appGlobalAuthKey = destinationAppKeyBundle.authKey,
            appRecoveryAuthKey = destinationAppKeyBundle.recoveryAuthKey,
            delayPeriod = account.config.delayNotifyDuration,
            hardwareAuthKey = destinationHardwareKeyBundle.authKey
          ).mapError {
            when (it) {
              is SpecificClientError<InitiateAccountDelayNotifyErrorCode> -> {
                when (it.errorCode) {
                  COMMS_VERIFICATION_REQUIRED -> CommsVerificationRequiredError(it.error)
                  RECOVERY_ALREADY_EXISTS -> RecoveryAlreadyExistsError(it.error)
                }
              }
              else -> OtherError(it.error)
            }
          }.bind()

        recoveryDao.setActiveServerRecovery(serviceResponse.serverRecovery)
          .mapError { OtherError(it) }
          .bind()
      }
    }

  override suspend fun cancelRecovery(): Result<Unit, CancelDelayNotifyRecoveryError> =
    coroutineBinding {
      recoveryLock.withLock {
        val account = accountService.getAccount<FullAccount>()
          .mapError(::LocalCancelDelayNotifyError)
          .bind()
        cancelDelayNotifyRecoveryF8eClient
          .cancel(
            f8eEnvironment = account.config.f8eEnvironment,
            fullAccountId = account.accountId,
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

        recoveryStatusService.clear()
          .mapError(::LocalCancelDelayNotifyError)
          .bind()
      }
    }
}
