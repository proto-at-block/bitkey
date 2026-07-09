package build.wallet.f8e.client.plugins

import build.wallet.firmware.FirmwareDeviceInfoDao
import com.github.michaelbull.result.get
import io.ktor.client.plugins.api.createClientPlugin

class FirmwareDeviceInfoPluginConfig {
  lateinit var firmwareDeviceInfoDao: FirmwareDeviceInfoDao
}

internal val FirmwareDeviceInfoPlugin = createClientPlugin(
  name = "firmware-device-info-plugin",
  createConfiguration = ::FirmwareDeviceInfoPluginConfig
) {
  val firmwareDeviceInfoDao = pluginConfig.firmwareDeviceInfoDao

  onRequest { request, _ ->
    firmwareDeviceInfoDao.getDeviceInfo().get()?.let { deviceInfo ->
      request.attributes.put(FirmwareDeviceInfoAttribute, deviceInfo)
    }
  }
}
