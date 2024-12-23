package build.wallet.platform.config

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.TouchpointPlatform.FcmTeam

@BitkeyInject(AppScope::class)
class DeviceTokenConfigProviderImpl : DeviceTokenConfigProvider {
  override suspend fun config() = DeviceTokenConfig("fake-device-token", FcmTeam)
}
