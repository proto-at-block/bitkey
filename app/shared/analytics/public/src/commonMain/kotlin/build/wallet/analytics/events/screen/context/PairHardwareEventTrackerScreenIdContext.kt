package build.wallet.analytics.events.screen.context

/**
 * Context for screens related to pairing a new HW device in the app.
 * Customers pair a new HW during new account creation as well as during HW recovery.
 */
enum class PairHardwareEventTrackerScreenIdContext : EventTrackerScreenIdContext {
  /** Events for pairing new hardware during new account creation */
  ACCOUNT_CREATION,

  /** Events for pairing new hardware during HW recovery */
  HW_RECOVERY,
}
