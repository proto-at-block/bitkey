package build.wallet.analytics.events.screen.id

enum class InheritanceEventTrackerScreenId : EventTrackerScreenId {
  /**
   * Initial screen when starting an inheritance claim to explain the process to the user.
   */
  StartClaimEducationScreen,

  /**
   * Screen confirming that the user wants to open a claim.
   */
  SubmitClaimPromptScreen,

  /**
   * Loading screen while the app attempts to open a claim.
   */
  SubmittingClaim,

  /**
   * Screen showing that a claim has successfully been started.
   */
  ClaimSubmitted,

  /**
   * Manage Inheritance screen.
   */
  ManageInheritance,
}
