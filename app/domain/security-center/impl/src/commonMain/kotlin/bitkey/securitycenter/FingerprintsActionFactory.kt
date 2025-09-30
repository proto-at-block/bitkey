package bitkey.securitycenter

import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.firmware.HardwareUnlockInfoService
import bitkey.privilegedactions.FingerprintResetService
import bitkey.privilegedactions.FingerprintResetState
import bitkey.privilegedactions.isDelayAndNotifyReadyToComplete
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.UnlockMethod
import com.github.michaelbull.result.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.datetime.Clock

interface FingerprintsActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class FingerprintsActionFactoryImpl(
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val fingerprintResetService: FingerprintResetService,
  private val clock: Clock,
) : FingerprintsActionFactory {
  override suspend fun create(): Flow<SecurityAction> {
    val fingerprintResetReadyFlow = createFingerprintResetReadyFlow()

    return combine(
      hardwareUnlockInfoService
        .countUnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS),
      fingerprintResetReadyFlow,
      firmwareDeviceInfoDao.deviceInfo()
    ) { count, resetReady, firmwareDeviceInfo ->
      FingerprintsAction(count, firmwareDeviceInfo.get(), resetReady)
    }
  }

  private fun createFingerprintResetReadyFlow(): Flow<Boolean> {
    return fingerprintResetService
      .fingerprintResetAction
      .combine(fingerprintResetService.pendingFingerprintResetGrant()) { actionInstance, grant ->
        when {
          // Persisted grant is ready to complete immediately
          grant != null -> FingerprintResetState.GrantReady(grant)

          // Check server-side action
          else -> {
            val delayAndNotify = actionInstance?.authorizationStrategy as? AuthorizationStrategy.DelayAndNotify
            when {
              actionInstance == null || delayAndNotify == null -> FingerprintResetState.None
              actionInstance.isDelayAndNotifyReadyToComplete(clock) -> FingerprintResetState.DelayCompleted(actionInstance)
              else -> FingerprintResetState.DelayInProgress(actionInstance, delayAndNotify)
            }
          }
        }
      }
      .distinctUntilChanged()
      .transformLatest { resetState: FingerprintResetState ->
        when (resetState) {
          is FingerprintResetState.GrantReady,
          is FingerprintResetState.DelayCompleted,
          -> {
            emit(true)
          }

          is FingerprintResetState.DelayInProgress -> {
            emit(false)
            val remaining = resetState.delayAndNotify.delayEndTime - clock.now()
            if (remaining.isPositive()) {
              delay(remaining.inWholeMilliseconds)
            }
            emit(true)
          }

          is FingerprintResetState.None -> {
            emit(false)
          }
        }
      }
  }
}
