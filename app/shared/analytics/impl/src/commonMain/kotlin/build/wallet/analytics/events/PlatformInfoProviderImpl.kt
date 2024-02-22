package build.wallet.analytics.events

import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import build.wallet.platform.versions.OsVersionInfoProvider

expect class PlatformInfoProviderImpl(
  platformContext: PlatformContext,
  appId: AppId,
  appVersion: String,
  osVersionInfoProvider: OsVersionInfoProvider,
) : PlatformInfoProvider
