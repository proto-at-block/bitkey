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

  /**
   * Managing a specific Benefactor.
   */
  ManageBenefactor,

  /**
   * Locking the claim and loading the transaction details.
   */
  LoadingClaimDetails,

  /**
   * Error screen shown when fails to lock/load a claim.
   */
  LoadingClaimDetailsFailure,

  /**
   * Details about the inheritance transfer before the user confirms.
   */
  ConfirmClaimTransfer,

  /**
   * Attempting to send the transaction to the server
   */
  StartingTransfer,

  /**
   * Error screen shown when fails to send the transaction.
   */
  TransferFailed,

  /**
   * Confirmation that the transaction has been sent.
   */
  ClaimComplete,
}
