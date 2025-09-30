package build.wallet.analytics.events.screen.id

enum class WalletMigrationEventTrackerScreenId : EventTrackerScreenId {
  /** Introduction screen explaining the private wallet migration */
  PRIVATE_WALLET_MIGRATION_INTRO,

  /** Success screen when private wallet migration is complete */
  PRIVATE_WALLET_MIGRATION_COMPLETE,

  /** Error screen when private wallet migration fails */
  PRIVATE_WALLET_MIGRATION_ERROR,
}
