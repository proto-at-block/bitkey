package build.wallet.analytics.events.screen.id

enum class GeneralEventTrackerScreenId : EventTrackerScreenId {
  /** The debug menu is showing */
  DEBUG_MENU,

  /** Initial loading screen of the app */
  SPLASH_SCREEN,

  /** Loading screen shown to the customer when we are doing some generic app setup work */
  LOADING_APP,

  /** Loading screen shown to the customer when we are setting up their new keybox */
  LOADING_SAVING_KEYBOX,

  /**
   * The customer is deciding how to access an account, either by creating a new one or
   * choosing "More Options".
   */
  CHOOSE_ACCOUNT_ACCESS,

  /**
   * The customer is deciding how to access an account after "More Options", either by
   * being a trusted contact or restoring a wallet.
   */
  ACCOUNT_ACCESS_MORE_OPTIONS,

  /**
   * Showing introduction for Being a Trusted Contact.
   */
  BEING_TRUSTED_CONTACT_INTRODUCTION,

  /**
   * App update modal prompting the user to update their app.
   */
  APP_UPDATE_MODAL,
}
