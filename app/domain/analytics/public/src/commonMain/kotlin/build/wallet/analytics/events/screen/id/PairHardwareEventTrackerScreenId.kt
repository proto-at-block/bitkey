package build.wallet.analytics.events.screen.id

enum class PairHardwareEventTrackerScreenId : EventTrackerScreenId {
  /** Error sheet shown when fingerprint enrollment was not yet complete but the customer tried to tap. */
  FINGERPRINT_ENROLLMENT_ERROR_SHEET,

  /** Instructions shown to the customer to activate (wake up) their HW  */
  HW_ACTIVATION_INSTRUCTIONS,

  /** Instructions shown to the customer to pair their hardware (start fingerprint enrollment) */
  HW_PAIR_INSTRUCTIONS,

  /** Instructions shown to the customer to save their fingerprints and complete HW pairing  */
  HW_SAVE_FINGERPRINT_INSTRUCTIONS,
}
