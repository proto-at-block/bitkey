package build.wallet.analytics.events.screen.id

enum class EmergencyAccessKitTrackerScreenId : EventTrackerScreenId {
  /** Screen offering the Emergency Access Kit import method */
  SELECT_IMPORT_METHOD,

  /** Screen for importing the Emergency Access Kit by pasting the mobile key */
  IMPORT_TEXT_KEY,

  /** Screen for scanning the QR code in the Emergency Access Kit */
  SCAN_QR_CODE,

  /** The entered or scanned mobile key was not a valid backup key */
  CODE_NOT_RECOGNIZED,

  /** Screen prior to starting the NFC communications to restore a wallet from the EAK */
  RESTORE_YOUR_WALLET,

  /** Loading screen while loading and importing the EAK backup */
  LOADING_BACKUP,
}
