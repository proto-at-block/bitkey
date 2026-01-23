package build.wallet.analytics.events.screen.id

enum class FwupEventTrackerScreenId : EventTrackerScreenId {
  /** Instructions shown to the customer to update their firmware */
  FWUP_UPDATE_INSTRUCTIONS,

  /** Error sheet shown when FWUP fails for some reason */
  FWUP_UPDATE_ERROR_SHEET,

  /** Error sheet shown when the HW was locked and the customer attempted to FWUP */
  FWUP_UNAUTHENTICATED_ERROR_SHEET,

  /** Screen shown when no firmware update is needed. Specific to FWUP. */
  NFC_NO_UPDATE_NEEDED_FWUP,

  /** Screen shown when the firmware update is in progress. Specific to FWUP. */
  NFC_UPDATE_IN_PROGRESS_FWUP,

  /** Screen shown when the firmware update was in progress but lost connection. Specific to FWUP. */
  NFC_DEVICE_LOST_CONNECTION_FWUP,

  /** Sheet prompting user to update firmware to add additional fingerprints */
  FWUP_FINGERPRINT_UPDATE_PROMPT_SHEET,
}
