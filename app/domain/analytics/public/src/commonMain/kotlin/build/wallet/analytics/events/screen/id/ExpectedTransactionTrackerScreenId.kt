package build.wallet.analytics.events.screen.id

enum class ExpectedTransactionTrackerScreenId : EventTrackerScreenId {
  /** Loading screen shown while loading partner info for an incoming transaction deeplink. */
  EXPECTED_TRANSACTION_NOTICE_LOADING,

  /** Screen showing the information about a new expected transaction after a partner deeplink */
  EXPECTED_TRANSACTION_NOTICE_DETAILS,
}
