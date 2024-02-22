package build.wallet.recovery

import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.requireRecoveryAuthKey
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyService
import build.wallet.recovery.LocalRecoveryAttemptProgress.CreatedPendingKeybundles
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError.F8eInitiateDelayNotifyError
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError.FailedToPersistRecoveryStateError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError

class LostHardwareRecoveryStarterImpl(
  private val initiateAccountDelayNotifyService: InitiateAccountDelayNotifyService,
  private val recoveryDao: RecoveryDao,
) : LostHardwareRecoveryStarter {
  override suspend fun initiate(
    activeKeybox: Keybox,
    destinationAppKeyBundle: AppKeyBundle,
    destinationHardwareKeyBundle: HwKeyBundle,
  ): Result<Unit, InitiateDelayNotifyHardwareRecoveryError> =
    binding {
      // Persist local pending recovery state
      recoveryDao.setLocalRecoveryProgress(
        CreatedPendingKeybundles(
          fullAccountId = activeKeybox.fullAccountId,
          appKeyBundle = destinationAppKeyBundle,
          hwKeyBundle = destinationHardwareKeyBundle,
          lostFactor = Hardware
        )
      ).mapError { FailedToPersistRecoveryStateError(it) }

      // Initiate delay period with f8e
      val serviceResponse =
        initiateAccountDelayNotifyService.initiate(
          f8eEnvironment = activeKeybox.config.f8eEnvironment,
          fullAccountId = activeKeybox.fullAccountId,
          lostFactor = Hardware,
          appGlobalAuthKey = destinationAppKeyBundle.authKey,
          appRecoveryAuthKey = destinationAppKeyBundle.requireRecoveryAuthKey(),
          delayPeriod = activeKeybox.config.delayNotifyDuration,
          hardwareAuthKey = destinationHardwareKeyBundle.authKey
        ).mapError { F8eInitiateDelayNotifyError(it) }.bind()

      recoveryDao.setActiveServerRecovery(serviceResponse.serverRecovery)
        .mapError { FailedToPersistRecoveryStateError(it) }
        .bind()
    }
}
