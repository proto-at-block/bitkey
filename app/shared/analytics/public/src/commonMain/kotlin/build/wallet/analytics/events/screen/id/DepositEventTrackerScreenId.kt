package build.wallet.analytics.events.screen.id

enum class DepositEventTrackerScreenId : EventTrackerScreenId {
  /** Sheet showing an option to buy or transfer from partners */
  PARTNERS_DEPOSIT_OPTIONS,

  /** Loading screen shown when we are loading transfer partners to show */
  LOADING_TRANSFER_PARTNERS,

  /** Loading screen shown when we are loading the redirect for a specific partner */
  LOADING_TRANSFER_PARTNER_REDIRECT,

  /** Error sheet shown when no transfer partners are found */
  TRANSFER_PARTNERS_NOT_FOUND_ERROR,

  /** Error sheet shown when the redirect for a partner fails */
  TRANSFER_PARTNER_REDIRECT_ERROR,

  /** Sheet showing a list of transfer partners */
  TRANSFER_PARTNERS_LIST,

  /** Sheet showing a list of purchase quotes from partners */
  PARTNER_QUOTES_LIST,
}
