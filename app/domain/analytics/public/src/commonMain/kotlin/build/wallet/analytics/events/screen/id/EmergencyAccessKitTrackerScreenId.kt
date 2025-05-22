package build.wallet.analytics.events.screen.id

enum class EmergencyAccessKitTrackerScreenId : EventTrackerScreenId {
  /** Screen offering the Emergency Exit Kit import method */
  SELECT_IMPORT_METHOD,

  /** Screen for importing the Emergency Exit Kit by pasting the App Key */
  IMPORT_TEXT_KEY,

  /** Screen for scanning the QR code in the Emergency Exit Kit */
  SCAN_QR_CODE,

  /** The entered or scanned App Key was not a valid backup key */
  CODE_NOT_RECOGNIZED,

  /** Screen prior to starting the NFC communications to restore a wallet from the EEK */
  RESTORE_YOUR_WALLET,

  /** Loading screen while loading and importing the EEK backup */
  LOADING_BACKUP,
}
