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

  /** Sheet shown when UTXO consolidation is required before private wallet migration */
  PRIVATE_WALLET_MIGRATION_UTXO_CONSOLIDATION_REQUIRED,

  /** Warning sheet shown when there are pending transactions that must be confirmed first */
  PRIVATE_WALLET_MIGRATION_PENDING_TRANSACTIONS_WARNING,
}
