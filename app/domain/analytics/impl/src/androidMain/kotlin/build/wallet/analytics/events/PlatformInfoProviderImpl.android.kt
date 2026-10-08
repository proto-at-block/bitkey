package build.wallet.analytics.events

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import build.wallet.analytics.v1.Client.CLIENT_ANDROID_APP
import build.wallet.analytics.v1.OSType.OS_TYPE_ANDROID
import build.wallet.analytics.v1.PlatformInfo
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVersion
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.versions.OsVersionInfoProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(AppScope::class)
class PlatformInfoProviderImpl(
  application: Application,
  appId: AppId,
  appVersion: AppVersion,
  private val clock: Clock,
  private val deviceInfoProvider: DeviceInfoProvider,
  osVersionInfoProvider: OsVersionInfoProvider,
) : PlatformInfoProvider {
  private val staticPlatformInfo by lazy {
    val securityPatch = Build.VERSION.SECURITY_PATCH
    val deviceInfo = deviceInfoProvider.getDeviceInfo()
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
      app_id = appId.value,
      os_security_patch = securityPatch,
      emulator_state = emulatorState(deviceInfo.isEmulator),
      android_strongbox_availability =
        androidStrongboxAvailability(
          sdkInt = Build.VERSION.SDK_INT,
          hasStrongboxFeature =
            application.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        )
    )
  }

  override fun getPlatformInfo(): PlatformInfo {
    return staticPlatformInfo.copy(
      os_security_patch_age_bucket =
        securityPatchAgeBucket(
          securityPatch = staticPlatformInfo.os_security_patch,
          today = clock.now().toLocalDateTime(TimeZone.UTC).date
        ),
      device_lock_state = analyticsDeviceLockState(deviceInfoProvider.getDeviceLockState())
    )
  }
}
