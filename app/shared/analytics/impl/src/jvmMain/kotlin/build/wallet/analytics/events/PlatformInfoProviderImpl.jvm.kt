package build.wallet.analytics.events

import build.wallet.analytics.v1.Client.CLIENT_UNSPECIFIED
import build.wallet.analytics.v1.OSType.OS_TYPE_UNSPECIFIED
import build.wallet.analytics.v1.PlatformInfo
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVersion
import build.wallet.platform.versions.OsVersionInfoProvider

actual class PlatformInfoProviderImpl actual constructor(
  platformContext: PlatformContext,
  appId: AppId,
  appVersion: AppVersion,
  osVersionInfoProvider: OsVersionInfoProvider,
) : PlatformInfoProvider {
  private val platformInfoLazy by lazy {
    PlatformInfo(
      client_type = CLIENT_UNSPECIFIED,
      application_version = appVersion.value,
      os_type = OS_TYPE_UNSPECIFIED,
      os_version = osVersionInfoProvider.getOsVersion(),
      app_id = appId.value
    )
  }

  actual override fun getPlatformInfo(): PlatformInfo {
    return platformInfoLazy
  }
}
