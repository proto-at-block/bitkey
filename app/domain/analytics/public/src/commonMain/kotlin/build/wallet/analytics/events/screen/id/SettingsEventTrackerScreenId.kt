package build.wallet.analytics.events.screen.id

enum class SettingsEventTrackerScreenId : EventTrackerScreenId {
  /** The settings screen is showing */
  SETTINGS,

  /** The customer electrum server screen from Settings is showing  */
  SETTINGS_CUSTOM_ELECTRUM_SERVER,

  /** The mobile pay screen from Settings is showing  */
  SETTINGS_MOBILE_PAY,

  /** The device info screen from Settings is showing  */
  SETTINGS_DEVICE_INFO,

  /** The device info screen from Settings is showing with empty device (device not found) state */
  SETTINGS_DEVICE_INFO_EMPTY,

  /** The settings screen for enabling biometrics is showing */
  SETTING_BIOMETRICS,

  /** The sheet for managing or resetting fingerprints is showing */
  SETTINGS_MANAGE_FINGERPRINTS_OPTIONS_SHEET,
}
