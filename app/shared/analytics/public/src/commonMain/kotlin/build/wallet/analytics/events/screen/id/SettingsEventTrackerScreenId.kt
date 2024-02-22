package build.wallet.analytics.events.screen.id

enum class SettingsEventTrackerScreenId : EventTrackerScreenId {
  /** The settings tab is showing */
  SETTINGS,

  /** The customer electrum server screen from Settings is showing  */
  SETTINGS_CUSTOM_ELECTRUM_SERVER,

  /** The mobile pay screen from Settings is showing  */
  SETTINGS_MOBILE_PAY,

  /** The notifications screen from Settings is showing  */
  SETTINGS_NOTIFICATIONS,

  /** The device info screen from Settings is showing  */
  SETTINGS_DEVICE_INFO,

  /** Error screen shown if there was no device info when Device tab in settings was tapped */
  SETTINGS_DEVICE_INFO_ERROR,

  /** The help center screen from Settings is showing  */
  SETTINGS_HELP_CENTER,

  /** The send feedback screen from Settings is showing */
  SETTINGS_SEND_FEEDBACK,
}
