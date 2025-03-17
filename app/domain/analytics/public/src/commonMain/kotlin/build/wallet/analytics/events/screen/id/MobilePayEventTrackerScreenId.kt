package build.wallet.analytics.events.screen.id

enum class MobilePayEventTrackerScreenId : EventTrackerScreenId {
  /** Loading screen shown while we load the limit from the db. Should be seen rarely, if ever. */
  MOBILE_PAY_LOADING,

  /** The instructions shown the customer to enable Mobile Pay from the Getting Started task */
  ENABLE_MOBILE_PAY_INSTRUCTIONS,

  /** The amount slider for choosing a mobile pay amount is showing */
  MOBILE_PAY_LIMIT_UPDATE_SLIDER,

  /** Error sheet shown when validating with hardware fails */
  MOBILE_PAY_LIMIT_UPDATE_HW_APPROVAL_ERROR_SHEET,

  /** Loading screen shown while we send the limit to the server */
  MOBILE_PAY_LIMIT_UPDATE_LOADING,

  /** Success screen shown when the limit is set */
  MOBILE_PAY_LIMIT_UPDATE_SUCCESS,

  /** Error screen shown when updating the Mobile Pay limit fails for some reason */
  MOBILE_PAY_LIMIT_UPDATE_FAILURE,
}
