package bitkey.securitycenter

import bitkey.account.HardwareType
import bitkey.verification.TxVerificationService
import build.wallet.availability.AppFunctionalityService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.TxVerificationFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoDao
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface TxVerificationActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class TxVerificationActionFactoryImpl(
  private val flag: TxVerificationFeatureFlag,
  private val txVerificationService: TxVerificationService,
  private val appFunctionalityService: AppFunctionalityService,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
) : TxVerificationActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    return combine(
      flag.flagValue(),
      txVerificationService.getCurrentThreshold(),
      appFunctionalityService.status,
      firmwareDeviceInfoDao.deviceInfo()
    ) { flag, threshold, status, deviceInfoResult ->
      val deviceInfo = deviceInfoResult.get()
      when {
        !flag.value || !threshold.isOk -> null
        deviceInfo?.hardwareType() == HardwareType.W3 -> null
        else -> TxVerificationAction(threshold.value, status.featureStates.send)
      }
    }
  }
}
