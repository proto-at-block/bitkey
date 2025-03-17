package build.wallet.analytics.events.screen.id

enum class NfcEventTrackerScreenId : EventTrackerScreenId {
  /** Error screen shown to the customer when NFC on their phone is not available */
  NFC_NOT_AVAILABLE,

  /** Instructions shown to the customer to turn NFC on their phone on */
  NFC_ENABLE_INSTRUCTIONS,

  /** Screen shown when the NFC connection is first opened */
  NFC_INITIATE,

  /** Screen shown when the NFC connection detects a tag */
  NFC_DETECTED,

  /** Screen shown when the NFC interaction succeeds */
  NFC_SUCCESS,

  /** Screen shown when the NFC interaction fails */
  NFC_FAILURE,
}
