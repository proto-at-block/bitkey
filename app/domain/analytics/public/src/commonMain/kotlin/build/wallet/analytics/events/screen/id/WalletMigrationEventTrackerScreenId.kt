package build.wallet.analytics.events.screen.id

enum class WalletMigrationEventTrackerScreenId : EventTrackerScreenId {
  /** Introduction screen explaining the private wallet migration */
  PRIVATE_WALLET_MIGRATION_INTRO,

  /** Fee estimate confirmation sheet for private wallet migration */
  PRIVATE_WALLET_MIGRATION_FEE_ESTIMATE,

  /** Creating keyset during private wallet migration */
  PRIVATE_WALLET_CREATING_KEYSET,

  /** Success screen when private wallet migration is complete */
  PRIVATE_WALLET_MIGRATION_COMPLETE,

  /** Error screen when private wallet migration fails */
  PRIVATE_WALLET_MIGRATION_ERROR,

  /** Generating private wallet migration sweep psbts */
  PRIVATE_WALLET_MIGRATION_SWEEP_GENERATING_PSBTS,

  /** Error generating private wallet migration sweep psbts */
  PRIVATE_WALLET_MIGRATION_SWEEP_GENERATING_PSBTS_ERROR,

  /** Private wallet migration sweep zero balance */
  PRIVATE_WALLET_MIGRATION_SWEEP_ZERO_BALANCE,

  /** Private wallet migration sweep sign psbts prompt */
  PRIVATE_WALLET_MIGRATION_SWEEP_SIGN_PSBTS_PROMPT,

  /** Broadcasting private wallet migration sweep */
  PRIVATE_WALLET_MIGRATION_SWEEP_BROADCASTING,

  /** Private wallet migration sweep success */
  PRIVATE_WALLET_MIGRATION_SWEEP_SUCCESS,

  /** Private wallet migration sweep failed */
  PRIVATE_WALLET_MIGRATION_SWEEP_FAILED,

  /** Warning screen shown on W3 when there are multiple transactions to sign during migration */
  PRIVATE_WALLET_MIGRATION_SWEEP_MULTIPLE_TRANSACTIONS_WARNING,

  /** Screen shown while awaiting hardware verification before private wallet migration sweep */
  PRIVATE_WALLET_MIGRATION_SWEEP_HARDWARE_VERIFICATION_REQUIRED,

  /** Sheet shown when UTXO consolidation is required before private wallet migration */
  PRIVATE_WALLET_MIGRATION_UTXO_CONSOLIDATION_REQUIRED,

  /** Warning sheet shown when there are pending transactions that must be confirmed first */
  PRIVATE_WALLET_MIGRATION_PENDING_TRANSACTIONS_WARNING,

  /** Introduction screen for W3 hardware upgrade */
  W3_UPGRADE_INTRO,

  /** Interstitial blocker promoting W3 hardware upgrade */
  W3_UPGRADE_BLOCKER,

  /** Interstitial reminder to wipe the old W1 after W3 upgrade */
  W3_UPGRADE_OLD_DEVICE_WIPE_READY,

  /** Screen asking if user has new device ready */
  W3_UPGRADE_DEVICE_READY,

  /** Screen showing old hardware instructions before sweep */
  W3_UPGRADE_OLD_HARDWARE_INSTRUCTIONS,

  /** Screen showing old hardware instructions before auth key rotation */
  W3_UPGRADE_OLD_HARDWARE_AUTH_ROTATION_INSTRUCTIONS,

  /** Screen showing new hardware instructions before auth key rotation */
  W3_UPGRADE_NEW_HARDWARE_AUTH_ROTATION_INSTRUCTIONS,

  /** Creating keyset during W3 upgrade */
  W3_UPGRADE_CREATING_KEYSET,

  /** Loading screen while checking the paired W3 hardware auth key */
  W3_UPGRADE_CHECKING_HARDWARE_AUTH_KEY_AVAILABILITY,

  /** Success screen when W3 upgrade is complete */
  W3_UPGRADE_COMPLETE,

  /** Error screen when W3 upgrade fails */
  W3_UPGRADE_ERROR,

  /** Error screen when paired W3 hardware auth key is already in use */
  W3_UPGRADE_HARDWARE_AUTH_KEY_IN_USE_ERROR,

  /** Error screen when wrong hardware type is tapped during W3 upgrade */
  W3_UPGRADE_WRONG_HARDWARE_ERROR,

  /** Warning sheet shown when pending transactions block W3 upgrade */
  W3_UPGRADE_PENDING_TRANSACTIONS_WARNING,

  /** Warning sheet shown when cloud backup is unhealthy and blocks W3 upgrade */
  W3_UPGRADE_CLOUD_BACKUP_UNHEALTHY_WARNING,

  /** Sheet shown when UTXO consolidation is required before W3 upgrade */
  W3_UPGRADE_UTXO_CONSOLIDATION_REQUIRED,

  /** Loading screen while generating new auth keys during W3 upgrade */
  W3_UPGRADE_GENERATING_AUTH_KEYS,

  /** Loading screen while preparing for auth key rotation during W3 upgrade */
  W3_UPGRADE_PREPARING_AUTH_ROTATION,

  /** Loading screen while running auth key rotation during W3 upgrade */
  W3_UPGRADE_RUNNING_AUTH_ROTATION,

  /** Loading screen while preparing action proof authorization during W3 upgrade */
  W3_UPGRADE_PREPARING_AUTHORIZATION,

  /** Loading screen while running server keyset activation during W3 upgrade */
  W3_UPGRADE_RUNNING_SERVER_KEYSET_ACTIVATION,

  /** Loading screen while checking for funds during W3 upgrade */
  W3_UPGRADE_CHECKING_FOR_FUNDS,

  /** Loading screen while resuming auth key rotation during W3 upgrade */
  W3_UPGRADE_RESUMING_AUTH_KEY_ROTATION,

  /** Initial loading screen while checking for in-progress W3 upgrade migration */
  W3_UPGRADE_LOADING,
}
