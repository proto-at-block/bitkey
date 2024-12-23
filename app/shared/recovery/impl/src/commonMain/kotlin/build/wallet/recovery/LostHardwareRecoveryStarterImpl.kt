package build.wallet.recovery

import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClient
import build.wallet.recovery.LocalRecoveryAttemptProgress.CreatedPendingKeybundles
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError.F8eInitiateDelayNotifyError
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError.FailedToPersistRecoveryStateError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class LostHardwareRecoveryStarterImpl(
  private val initiateAccountDelayNotifyF8eClient: InitiateAccountDelayNotifyF8eClient,
  private val recoveryDao: RecoveryDao,
) : LostHardwareRecoveryStarter {
  override suspend fun initiate(
    activeKeybox: Keybox,
    destinationAppKeyBundle: AppKeyBundle,
    destinationHardwareKeyBundle: HwKeyBundle,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<Unit, InitiateDelayNotifyHardwareRecoveryError> =
    coroutineBinding {
      // Persist local pending recovery state
      recoveryDao.setLocalRecoveryProgress(
        CreatedPendingKeybundles(
          fullAccountId = activeKeybox.fullAccountId,
          appKeyBundle = destinationAppKeyBundle,
          hwKeyBundle = destinationHardwareKeyBundle,
          appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
          lostFactor = Hardware
        )
      ).mapError { FailedToPersistRecoveryStateError(it) }

      // Initiate delay period with f8e
      val serviceResponse =
        initiateAccountDelayNotifyF8eClient.initiate(
          f8eEnvironment = activeKeybox.config.f8eEnvironment,
          fullAccountId = activeKeybox.fullAccountId,
          lostFactor = Hardware,
          appGlobalAuthKey = destinationAppKeyBundle.authKey,
          appRecoveryAuthKey = destinationAppKeyBundle.recoveryAuthKey,
          delayPeriod = activeKeybox.config.delayNotifyDuration,
          hardwareAuthKey = destinationHardwareKeyBundle.authKey
        ).mapError { F8eInitiateDelayNotifyError(it) }.bind()

      recoveryDao.setActiveServerRecovery(serviceResponse.serverRecovery)
        .mapError { FailedToPersistRecoveryStateError(it) }
        .bind()
    }
}
