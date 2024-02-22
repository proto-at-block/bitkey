package build.wallet.analytics.events.screen.context

/**
 * Context for push notification related screens in the app.
 * Push notifications are used both during new account creation as well as app recovery.
 */
enum class PushNotificationEventTrackerScreenIdContext : EventTrackerScreenIdContext {
  /** Events for setting up push notification permissions during new account creation */
  ACCOUNT_CREATION,

  /** Events for setting up push notification permissions during app recovery */
  APP_RECOVERY,

  /** Events for setting up push notification permissions during social recovery challenge */
  SOCIAL_RECOVERY_CHALLENGE,
}
