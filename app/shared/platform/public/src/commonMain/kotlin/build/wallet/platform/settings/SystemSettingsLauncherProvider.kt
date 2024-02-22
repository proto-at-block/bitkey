package build.wallet.platform.settings

interface SystemSettingsLauncherProvider {
  fun initialize(provider: () -> SystemSettingsLauncher)

  /** Returns [SystemSettingsLauncher] from the initialized provider. */
  fun get(): SystemSettingsLauncher
}
