package build.wallet.analytics.events

import build.wallet.analytics.v1.HardwareInfo

interface HardwareInfoProvider {
  suspend fun getHardwareInfo(): HardwareInfo
}
