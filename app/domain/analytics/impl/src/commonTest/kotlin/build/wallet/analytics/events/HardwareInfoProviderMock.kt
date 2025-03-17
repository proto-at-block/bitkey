package build.wallet.analytics.events

import build.wallet.analytics.v1.HardwareInfo

class HardwareInfoProviderMock : HardwareInfoProvider {
  override suspend fun getHardwareInfo(): HardwareInfo {
    return HardwareInfo(
      firmware_version = "fw_version_1",
      hw_model = "abcdef01",
      hw_manufacture_info = "234",
      hw_paired = true
    )
  }
}
