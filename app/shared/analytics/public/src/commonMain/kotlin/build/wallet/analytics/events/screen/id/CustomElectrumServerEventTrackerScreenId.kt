package build.wallet.analytics.events.screen.id

enum class CustomElectrumServerEventTrackerScreenId : EventTrackerScreenId {
  /** Screen to update the custom electrum server */
  CUSTOM_ELECTRUM_SERVER_UPDATE,

  /** Loading screen while we set the custom server */
  CUSTOM_ELECTRUM_SERVER_UPDATE_LOADING,

  /** Success screen shown when custom server was set */
  CUSTOM_ELECTRUM_SERVER_UPDATE_SUCCESS,

  /** Error screen shown when updating the customer electrum server fails */
  CUSTOM_ELECTRUM_SERVER_UPDATE_ERROR,
}
