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

  /**
   * Screen informing the user that their claim is complete but the
   * benefactor's wallet is empty.
   */
  ClaimEmpty,

  /**
   * Canceling the claim.
   */
  CancelingClaim,

  /**
   * Upsell screen for inheritance.
   */
  Upsell,

  /**
   * Screen to show the promo code upsell
   */
  PromoCodeUpsell,

  /**
   * Loading screen shown when we look up a claim for denial
   */
  StartDenyClaim,

  /**
   * Modal screen shown with the option to deny a submitted claim.
   */
  DenyClaim,

  /**
   * Screen shown when we attempt to remove a benefactor with an approved claim.
   */
  BeneficiaryApprovedClaimWarning,

  /**
   * Screen show when we attempt to remove a beneficiary with an approved claim
   */
  BenefactorApprovedClaimWarning,
}
