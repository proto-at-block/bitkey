package build.wallet.analytics.events.screen.id

enum class NfcEventTrackerScreenId : EventTrackerScreenId {
  /** Error screen shown to the customer when NFC on their phone is not available */
  NFC_NOT_AVAILABLE,

  /** Instructions shown to the customer to turn NFC on their phone on */
  NFC_ENABLE_INSTRUCTIONS,

  /** Screen shown when the NFC connection is first opened */
  NFC_INITIATE,

  /** Sheet shown by fake hardware to emulate an on-device approve/deny confirmation */
  NFC_EMULATED_HARDWARE_CONFIRMATION,

  /** Screen shown when the NFC connection detects a tag */
  NFC_DETECTED,

  /** Screen shown when the NFC interaction succeeds */
  NFC_SUCCESS,

  /** Screen shown when the NFC interaction fails */
  NFC_FAILURE,

  /** Placeholder help screen shown from the Android NFC flow */
  NFC_HELP,

  /**
   * W3 two-tap flow: Screen shown when user taps before approving/denying on the device.
   * Prompts user to make a decision on the hardware device.
   */
  NFC_CONFIRMATION_PENDING,

  /**
   * W3 two-tap flow: Screen shown when user explicitly denied on the device.
   * Acknowledges denial and allows retry.
   */
  NFC_CONFIRMATION_DENIED,
}
