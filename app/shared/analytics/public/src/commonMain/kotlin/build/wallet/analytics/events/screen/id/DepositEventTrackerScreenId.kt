package build.wallet.analytics.events.screen.id

enum class DepositEventTrackerScreenId : EventTrackerScreenId {
  /** Sheet showing an option to buy or transfer from partners */
  PARTNERS_DEPOSIT_OPTIONS,

  /** Loading screen shown when we are loading transfer partners to show */
  LOADING_TRANSFER_PARTNERS,

  /** Loading screen shown when we are redirecting to a specific partner for transfer */
  TRANSFER_PARTNER_REDIRECTING,

  /** Loading screen shown when we are loading the redirect for a specific partner */
  LOADING_TRANSFER_PARTNER_REDIRECT,

  /** Error sheet shown when no transfer partners are found */
  TRANSFER_PARTNERS_NOT_FOUND_ERROR,

  /** Error sheet shown when no transfer partners are available */
  TRANSFER_PARTNERS_NOT_AVAILABLE,

  /** Error sheet shown when the redirect for a partner fails */
  TRANSFER_PARTNER_REDIRECT_ERROR,

  /** Sheet showing a list of transfer partners */
  TRANSFER_PARTNERS_LIST,

  /** Sheet showing a list of purchase quotes from partners */
  PARTNER_QUOTES_LIST,

  /** Loading screen shown when we are loading the list of purchase quotes from partners */
  LOADING_PARTNER_QUOTES_LIST,

  /** Error sheet shown when loading quotes fails */
  PARTNER_QUOTES_LIST_ERROR,

  /** Sheet showing a list of purchase amount options */
  PARTNER_PURCHASE_OPTIONS,

  /** Loading screen shown when we are loading the list of purchase amount options */
  LOADING_PARTNER_PURCHASE_OPTIONS,

  /** Error sheet shown when loading purchase amount options fails */
  PARTNER_PURCHASE_OPTIONS_ERROR,

  /** Error sheet shown when no purchase options are available */
  PARTNER_PURCHASE_OPTIONS_NOT_AVAILABLE,

  /** Sheet showing a list of purchase amount options */
  CUSTOM_PARTNER_PURCHASE_AMOUNT,

  /** Loading screen shown when we are redirecting to a specific partner for purchase */
  PURCHASE_PARTNER_REDIRECTING,

  /** Loading screen shown when we are loading the redirect for a specific partner purchase */
  LOADING_PURCHASE_PARTNER_REDIRECT,

  /** Error sheet shown when the redirect for a partner fails */
  PURCHASE_PARTNER_REDIRECT_ERROR,
}
