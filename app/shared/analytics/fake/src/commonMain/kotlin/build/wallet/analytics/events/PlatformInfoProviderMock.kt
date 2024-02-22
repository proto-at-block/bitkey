package build.wallet.analytics.events

import build.wallet.analytics.v1.Client.CLIENT_ANDROID_APP
import build.wallet.analytics.v1.OSType.OS_TYPE_ANDROID
import build.wallet.analytics.v1.PlatformInfo

class PlatformInfoProviderMock : PlatformInfoProvider {
  override fun getPlatformInfo(): PlatformInfo {
    return PlatformInfo(
      device_id = "android_id_1",
      client_type = CLIENT_ANDROID_APP,
      application_version = "2023.1.3",
      os_type = OS_TYPE_ANDROID,
      os_version = "version_num_1",
      device_make = "make_1",
      device_model = "model_1",
      app_id = "app.id"
    )
  }
}
