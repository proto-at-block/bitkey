package build.wallet.analytics.events.screen.id

enum class CurrencyEventTrackerScreenId : EventTrackerScreenId {
  /** Screen showing the options for fiat and BTC preferences */
  CURRENCY_PREFERENCE,

  /** Screen showing a list of fiat currencies to choose from */
  CURRENCY_FIAT_LIST_SELECTION,

  /** Bottom sheet alerting the customer that Mobile Pay was disabled after a currency change */
  CURRENCY_CHANGE_RE_ENABLE_MOBILE_PAY_SHEET,

  /** Loading screen shown when we are completing currency preference during onboarding */
  SAVE_CURRENCY_PREFERENCE_LOADING,
}
