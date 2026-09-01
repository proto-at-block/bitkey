package build.wallet.analytics.events.screen.id

enum class MoneyHomeEventTrackerScreenId : EventTrackerScreenId {
  /** The list of all transactions from Money Home is showing  */
  MONEY_HOME_ALL_TRANSACTIONS,

  /** The Money Home screen is showing  */
  MONEY_HOME,

  /** The wallet is reloading local history before Money Home is shown. */
  MONEY_HOME_RELOADING_WALLET_HISTORY,

  /** Reloading wallet history before Money Home failed. */
  MONEY_HOME_RELOADING_WALLET_HISTORY_FAILED,

  /** Detail screen for a transaction is showing  */
  TRANSACTION_DETAIL,

  /** Screen for editing a transaction note. */
  TRANSACTION_NOTE_EDIT,

  /** The screen for a failed partner transaction is showing  */
  FAILED_PARTNER_TRANSACTION,

  /** Interstitial warning screen shown when wallet is at risk */
  WALLET_AT_RISK_INTERSTITIAL,
}
