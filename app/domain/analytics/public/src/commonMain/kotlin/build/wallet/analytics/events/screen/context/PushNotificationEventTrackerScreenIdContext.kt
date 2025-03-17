package build.wallet.analytics.events.screen.context

import build.wallet.analytics.events.EventTrackerContext

/**
 * Context for push notification related screens in the app.
 * Push notifications are used both during new account creation as well as app recovery.
 */
enum class PushNotificationEventTrackerScreenIdContext : EventTrackerContext {
  /** Events for setting up push notification permissions during new account creation */
  ACCOUNT_CREATION,

  /** Events for setting up push notification permissions during app recovery */
  APP_RECOVERY,

  /** Events for setting up push notification permissions during social recovery challenge */
  SOCIAL_RECOVERY_CHALLENGE,

  /** Events for setting up push notification permissions when starting an inheritance claim */
  INHERITANCE_CLAIM,
}
