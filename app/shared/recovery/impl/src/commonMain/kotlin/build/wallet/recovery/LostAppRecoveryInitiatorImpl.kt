package build.wallet.recovery

import build.wallet.bitkey.app.requireRecoveryAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyService
import build.wallet.keybox.builder.KeyCrossBuilder
import build.wallet.logging.logFailure
import build.wallet.recovery.LocalRecoveryAttemptProgress.CreatedPendingKeybundles
import build.wallet.recovery.LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError
import build.wallet.recovery.LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.F8eInitiateDelayNotifyError
import build.wallet.recovery.LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.FailedToPersistRecoveryStateError
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError

class LostAppRecoveryInitiatorImpl(
  private val initiateAccountDelayNotifyService: InitiateAccountDelayNotifyService,
  private val keyCrossBuilder: KeyCrossBuilder,
  private val recoveryDao: RecoveryDao,
) : LostAppRecoveryInitiator {
  override suspend fun initiate(
    keyboxConfig: KeyboxConfig,
    hardwareKeysForRecovery: HardwareKeysForRecovery,
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, InitiateDelayNotifyAppRecoveryError> =
    binding {
      // Generate app keys
      val newAppKeys = keyCrossBuilder.createNewKeyCross(keyboxConfig)

      // Persist local pending recovery state
      recoveryDao
        .setLocalRecoveryProgress(
          CreatedPendingKeybundles(
            fullAccountId = fullAccountId,
            appKeyBundle = newAppKeys.appKeyBundle,
            hwKeyBundle = hardwareKeysForRecovery.newKeyBundle,
            lostFactor = App
          )
        )
        .logFailure { "Failed to set local recovery progress when initiating DN recovery for Lost App." }
        .mapError(::FailedToPersistRecoveryStateError)
        .bind()

      // Initiate recovery with f8e
      val serverRecovery =
        initiateAccountDelayNotifyService
          .initiate(
            f8eEnvironment = f8eEnvironment,
            fullAccountId = fullAccountId,
            lostFactor = App,
            appGlobalAuthKey = newAppKeys.appKeyBundle.authKey,
            appRecoveryAuthKey = newAppKeys.appKeyBundle.requireRecoveryAuthKey(),
            hwFactorProofOfPossession = hwFactorProofOfPossession,
            delayPeriod = keyboxConfig.delayNotifyDuration,
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
