package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class SystemSettingsLauncherProviderImpl : SystemSettingsLauncherProvider {
  private lateinit var provider: () -> SystemSettingsLauncher

  override fun initialize(provider: () -> SystemSettingsLauncher) {
    this.provider = provider
  }

  override fun get() = provider()
}
