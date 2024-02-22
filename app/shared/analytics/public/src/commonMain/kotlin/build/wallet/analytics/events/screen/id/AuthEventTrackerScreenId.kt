package build.wallet.analytics.events.screen.id

enum class AuthEventTrackerScreenId : EventTrackerScreenId {
  /** Loading screen shown when refreshing auth tokens */
  REFRESHING_AUTH_TOKENS_FOR_HW_POP,

  /** Error screen shown when refreshing auth tokens fails */
  AUTH_TOKENS_REFRESH_FOR_HW_POP_ERROR,

  /** Provides the customer with a choice to sign out other devices after cloud restoration. */
  DECIDE_IF_SHOULD_ROTATE_AUTH_AFTER_CLOUD_RESTORE,

  /** Provides the customer with a choice to sign out other devices when opened from settings. */
  DECIDE_IF_SHOULD_ROTATE_AUTH_FROM_SETTINGS,

  /** Loading screen shown when customer chooses to sign out other devices after cloud restoration. */
  ROTATING_AUTH_AFTER_CLOUD_RESTORE,

  /** Loading screen shown when customer chooses to sign out other devices from settings. */
  ROTATING_AUTH_FROM_SETTINGS,

  /** Loading screen shown when customer chooses to v after cloud restoration. */
  SETTING_ACTIVE_KEYBOX_AFTER_CLOUD_RESTORE,

  /** Loading screen shown when persisting new keys after customer choose to sign out other devices from settings. */
  SETTING_ACTIVE_KEYBOX_FROM_SETTINGS,

  /** Success screen shown when customer chooses to sign out other devices after cloud restoration. */
  SUCCESSFULLY_ROTATED_AUTH_AFTER_CLOUD_RESTORE,

  /** Success screen shown when customer chooses to sign out other devices from settings. */
  SUCCESSFULLY_ROTATED_AUTH_FROM_SETTINGS,

  /** Error screen shown when customer chooses to sign out other devices after cloud restoration,
   * but it fails. */
  FAILED_TO_ROTATE_AUTH_AFTER_CLOUD_BACKUP,

  /** Error screen shown when customer chooses to sign out other devices after from settings,
   * but it fails. */
  FAILED_TO_ROTATE_AUTH_FROM_SETTINGS,
}
