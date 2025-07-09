package bitkey.securitycenter

import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.isDelayAndNotifyReadyToComplete
import bitkey.firmware.HardwareUnlockInfoService
import bitkey.privilegedactions.FingerprintResetService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.UnlockMethod
import build.wallet.home.GettingStartedTaskDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.datetime.Clock

interface FingerprintsActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class FingerprintsActionFactoryImpl(
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val fingerprintResetService: FingerprintResetService,
  private val clock: Clock,
) : FingerprintsActionFactory {
  override suspend fun create(): Flow<SecurityAction> {
    val gettingStartedTasks = gettingStartedTaskDao.getTasks()
    val firmwareDeviceInfo = firmwareDeviceInfoDao.deviceInfo().first().value
    val fingerprintResetReadyFlow = createFingerprintResetReadyFlow()

    return hardwareUnlockInfoService
      .countUnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS)
      .combine(fingerprintResetReadyFlow) { count, resetReady ->
        FingerprintsAction(gettingStartedTasks, count, firmwareDeviceInfo, resetReady)
      }
  }

  private fun createFingerprintResetReadyFlow(): Flow<Boolean> {
    return fingerprintResetService
      .fingerprintResetAction()
      .transformLatest { actionInstance ->
        val delayAndNotify =
          actionInstance?.authorizationStrategy as? AuthorizationStrategy.DelayAndNotify

        if (actionInstance == null || delayAndNotify == null) {
          emit(false)
          return@transformLatest
        }

        val readyNow = actionInstance.isDelayAndNotifyReadyToComplete(clock)

        if (readyNow) {
          emit(true)
        } else {
          emit(false)

          val remaining = delayAndNotify.delayEndTime - clock.now()
          if (remaining.isPositive()) {
            delay(remaining.inWholeMilliseconds)
          }

          emit(true)
        }
      }
      .distinctUntilChanged()
  }
}
