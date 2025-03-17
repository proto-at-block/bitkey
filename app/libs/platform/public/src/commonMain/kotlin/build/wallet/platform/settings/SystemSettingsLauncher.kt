package build.wallet.platform.settings

/**
 * A utility for wrapping native apis to launch system settings
 */
interface SystemSettingsLauncher {
  /**
   * Launch the native system settings specific to Bitkey app
   */
  fun launchAppSettings()

  /**
   * Launch the native system settings to the security settings
   */
  fun launchSecuritySettings()
}
