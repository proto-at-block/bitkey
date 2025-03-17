package build.wallet.analytics.events

import android.app.Application
import android.os.Build
import android.provider.Settings
import build.wallet.analytics.v1.Client.CLIENT_ANDROID_APP
import build.wallet.analytics.v1.OSType.OS_TYPE_ANDROID
import build.wallet.analytics.v1.PlatformInfo
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVersion
import build.wallet.platform.versions.OsVersionInfoProvider

@BitkeyInject(AppScope::class)
class PlatformInfoProviderImpl(
  application: Application,
  appId: AppId,
  appVersion: AppVersion,
  osVersionInfoProvider: OsVersionInfoProvider,
) : PlatformInfoProvider {
  private val platformInfoLazy by lazy {
    PlatformInfo(
      device_id =
        Settings.Secure.getString(
          application.contentResolver,
          Settings.Secure.ANDROID_ID
        ),
      client_type = CLIENT_ANDROID_APP,
      application_version = appVersion.value,
      os_type = OS_TYPE_ANDROID,
      os_version = osVersionInfoProvider.getOsVersion(),
      device_make = Build.BRAND,
      device_model = Build.MODEL,
      app_id = appId.value
    )
  }

  override fun getPlatformInfo(): PlatformInfo {
    return platformInfoLazy
  }
}
