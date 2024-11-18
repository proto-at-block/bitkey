package build.wallet.analytics.events

import build.wallet.analytics.v1.PlatformInfo
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import build.wallet.platform.config.AppVersion
import build.wallet.platform.versions.OsVersionInfoProvider

expect class PlatformInfoProviderImpl(
  platformContext: PlatformContext,
  appId: AppId,
  appVersion: AppVersion,
  osVersionInfoProvider: OsVersionInfoProvider,
) : PlatformInfoProvider {
  override fun getPlatformInfo(): PlatformInfo
}
