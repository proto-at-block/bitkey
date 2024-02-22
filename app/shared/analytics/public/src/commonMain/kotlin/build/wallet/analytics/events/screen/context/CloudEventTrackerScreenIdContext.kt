package build.wallet.analytics.events.screen.context

/**
 * Context for cloud storage related screens in the app.
 * Cloud storage is used both during new account creation as well as app recovery.
 */
enum class CloudEventTrackerScreenIdContext : EventTrackerScreenIdContext {
  /** Cloud events during new account creation */
  ACCOUNT_CREATION,

  /** Cloud events during app recovery */
  APP_RECOVERY,

  /** Cloud events during the initial get started experience */
  GET_STARTED,

  /** Cloud events during Cloud Backup repair as part of Cloud Backup Health experience. */
  BACKUP_REPAIR,
}
