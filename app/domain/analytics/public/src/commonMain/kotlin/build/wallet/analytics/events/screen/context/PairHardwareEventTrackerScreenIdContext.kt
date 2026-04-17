package build.wallet.analytics.events.screen.context

import build.wallet.analytics.events.EventTrackerContext

/**
 * Context for screens related to pairing a new HW device in the app.
 * Customers pair a new HW during new account creation as well as during HW recovery.
 */
enum class PairHardwareEventTrackerScreenIdContext : EventTrackerContext {
  /** Events for pairing new hardware during new account creation */
  ACCOUNT_CREATION,

  /** Events for pairing new hardware during HW recovery */
  HW_RECOVERY,

  /** Events for resetting fingerprints during hardware pairing */
  RESET_FINGERPRINTS,

  /** Events for pairing new hardware during W3 upgrade */
  PAIR_NEW_DEVICE_DURING_W3_UPGRADE,
}
