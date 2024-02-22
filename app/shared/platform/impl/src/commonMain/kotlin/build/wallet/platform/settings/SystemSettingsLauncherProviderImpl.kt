package build.wallet.platform.settings

class SystemSettingsLauncherProviderImpl : SystemSettingsLauncherProvider {
  private lateinit var provider: () -> SystemSettingsLauncher

  override fun initialize(provider: () -> SystemSettingsLauncher) {
    this.provider = provider
  }

  override fun get() = provider()
}
