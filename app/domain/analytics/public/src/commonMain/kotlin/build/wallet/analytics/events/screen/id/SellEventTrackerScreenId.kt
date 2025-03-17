package build.wallet.analytics.events.screen.id

enum class SellEventTrackerScreenId : EventTrackerScreenId {
  /** Screen showing a list of quotes from partners for selling */
  SELL_QUOTES_LIST,

  /** Loading screen shown when we are loading sell partners */
  LOADING_SELL_PARTNERS,

  /** Error screen shown when no sell partners are found */
  SELL_PARTNERS_NOT_AVAILABLE,

  /** Error screen shown when we are unable to load sell partners */
  SELL_PARTNERS_NOT_FOUND_ERROR,

  /** Loading screen shown while we load the partnership transaction */
  SELL_LOADING_TRANSACTION_DETAILS,

  /** Error screen shown when we are unable to load the partnership transaction */
  SELL_TRANSACTION_DETAILS_ERROR,

  /** Loading screen shown when we are loading the sell partner redirect */
  LOADING_SELL_PARTNER_REDIRECT,

  /** Screen shown when we are redirecting to the sell partner */
  SELL_PARTNER_REDIRECTING,

  /** Error screen shown when we are unable to load the sell partner redirect */
  SELL_PARTNER_REDIRECT_ERROR,

  /** Error screen shown when the sell transfer failed */
  SELL_TRANSFER_FAILED,

  /** Success screen shown once the bitcoin to sell has been transferred */
  SELL_PARTNERS_ON_THE_WAY_SUCCESS,
}
