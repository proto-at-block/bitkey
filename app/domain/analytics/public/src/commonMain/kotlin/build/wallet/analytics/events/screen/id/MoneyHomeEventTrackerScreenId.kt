package build.wallet.analytics.events.screen.id

enum class MoneyHomeEventTrackerScreenId : EventTrackerScreenId {
  /** The list of all transactions from Money Home is showing  */
  MONEY_HOME_ALL_TRANSACTIONS,

  /** The Money Home screen is showing  */
  MONEY_HOME,

  /** Detail screen for a transaction is showing  */
  TRANSACTION_DETAIL,

  /** The screen for a failed partner transaction is showing  */
  FAILED_PARTNER_TRANSACTION,

  /** Interstitial warning screen shown when wallet is at risk */
  WALLET_AT_RISK_INTERSTITIAL,
}
