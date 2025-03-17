package build.wallet.recovery

import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClient
import build.wallet.recovery.LocalRecoveryAttemptProgress.CreatedPendingKeybundles
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.sync.withLock

@BitkeyInject(AppScope::class)
class LostHardwareRecoveryStarterImpl(
  private val initiateAccountDelayNotifyF8eClient: InitiateAccountDelayNotifyF8eClient,
  private val recoveryDao: RecoveryDao,
  private val recoveryLock: RecoveryLock,
  private val accountService: AccountService,
) : LostHardwareRecoveryStarter {
  override suspend fun initiate(
    destinationAppKeyBundle: AppKeyBundle,
    destinationHardwareKeyBundle: HwKeyBundle,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<Unit, InitiateDelayNotifyHardwareRecoveryError> =
    coroutineBinding {
      recoveryLock.withLock {
        val account = accountService.getAccount<FullAccount>()
          .mapError(::LocalAccountMissing)
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
        ).mapError { FailedToPersistRecoveryStateError(it) }

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
          ).mapError { F8eInitiateDelayNotifyError(it) }.bind()

        recoveryDao.setActiveServerRecovery(serviceResponse.serverRecovery)
          .mapError { FailedToPersistRecoveryStateError(it) }
          .bind()
      }
    }
}
