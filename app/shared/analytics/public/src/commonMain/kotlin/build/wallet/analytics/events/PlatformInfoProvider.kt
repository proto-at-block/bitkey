package build.wallet.analytics.events

import build.wallet.analytics.v1.PlatformInfo

interface PlatformInfoProvider {
  fun getPlatformInfo(): PlatformInfo
}
