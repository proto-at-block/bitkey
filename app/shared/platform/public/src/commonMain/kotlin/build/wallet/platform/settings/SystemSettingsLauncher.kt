package build.wallet.platform.settings

/**
 * A utility for wrapping native apis to launch system settings
 */
interface SystemSettingsLauncher {
  /**
   * Launch the native system settings
   */
  fun launchSettings()
}
