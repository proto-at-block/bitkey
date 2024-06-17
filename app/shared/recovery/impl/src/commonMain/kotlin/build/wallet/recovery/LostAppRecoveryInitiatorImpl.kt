package build.wallet.recovery

import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClient
import build.wallet.logging.logFailure
import build.wallet.recovery.LocalRecoveryAttemptProgress.CreatedPendingKeybundles
import build.wallet.recovery.LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError
import build.wallet.recovery.LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.F8eInitiateDelayNotifyError
import build.wallet.recovery.LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.FailedToPersistRecoveryStateError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

class LostAppRecoveryInitiatorImpl(
  private val initiateAccountDelayNotifyF8eClient: InitiateAccountDelayNotifyF8eClient,
  private val recoveryDao: RecoveryDao,
) : LostAppRecoveryInitiator {
  override suspend fun initiate(
    fullAccountConfig: FullAccountConfig,
    hardwareKeysForRecovery: HardwareKeysForRecovery,
    newAppKeys: AppKeyBundle,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, InitiateDelayNotifyAppRecoveryError> =
    coroutineBinding {
      // Persist local pending recovery state
      recoveryDao
        .setLocalRecoveryProgress(
          CreatedPendingKeybundles(
            fullAccountId = fullAccountId,
            appKeyBundle = newAppKeys,
            hwKeyBundle = hardwareKeysForRecovery.newKeyBundle,
            lostFactor = App,
            appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
          )
        )
        .logFailure { "Failed to set local recovery progress when initiating DN recovery for Lost App." }
        .mapError(::FailedToPersistRecoveryStateError)
        .bind()

      // Initiate recovery with f8e
      val serverRecovery =
        initiateAccountDelayNotifyF8eClient
          .initiate(
            f8eEnvironment = f8eEnvironment,
            fullAccountId = fullAccountId,
            lostFactor = App,
            appGlobalAuthKey = newAppKeys.authKey,
            appRecoveryAuthKey = newAppKeys.recoveryAuthKey,
            hwFactorProofOfPossession = hwFactorProofOfPossession,
            delayPeriod = fullAccountConfig.delayNotifyDuration,
            hardwareAuthKey = hardwareKeysForRecovery.newKeyBundle.authKey
          )
          .mapError { F8eInitiateDelayNotifyError(it) }
          .bind()
          .serverRecovery

      recoveryDao.setActiveServerRecovery(serverRecovery)
        .logFailure { "Failed to set active server recovery when initiating DN recovery for Lost App." }
        .mapError(::FailedToPersistRecoveryStateError)
        .bind()
    }
}
