package build.wallet.analytics.events

import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.v1.HardwareInfo
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.platform.hardware.SerialNumberParser
import com.github.michaelbull.result.get

@BitkeyInject(AppScope::class)
class HardwareInfoProviderImpl(
  private val appInstallationDao: AppInstallationDao,
  private val serialNumberParser: SerialNumberParser,
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
) : HardwareInfoProvider {
  override suspend fun getHardwareInfo(): HardwareInfo {
    val serialNumber =
      appInstallationDao.getOrCreateAppInstallation()
        .get()
        ?.hardwareSerialNumber
        .orEmpty()

    val firmwareVersion =
      firmwareDeviceInfoDao.getDeviceInfo()
        .get()
        ?.version
        .orEmpty()

    val serialNumberComponents = serialNumberParser.parse(serialNumber)
    return HardwareInfo(
      firmware_version = firmwareVersion,
      hw_model = serialNumberComponents.model.orEmpty(),
      hw_manufacture_info = serialNumberComponents.manufactureInfo.orEmpty(),
      hw_paired = serialNumberComponents.model != null,
      serial_number = serialNumber
    )
  }
}
