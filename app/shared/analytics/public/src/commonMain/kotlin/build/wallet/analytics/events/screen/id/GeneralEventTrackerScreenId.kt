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
   * recovering an existing one.
   */
  CHOOSE_ACCOUNT_ACCESS,

  /**
   * We are showing the very first screen with a button for the customer to 'Get Started'.
   */
  BITKEY_GET_STARTED,

  /**
   * Showing introduction for Being a Trusted Contact.
   */
  BEING_TRUSTED_CONTACT_INTRODUCTION,
}
