package build.wallet.analytics.events.screen.id

/**
 * Screen IDs for inheritance-related screens.
 *
 * Note: This file uses PascalCase naming (e.g., `StartClaimEducationScreen`) instead of the
 * standard SCREAMING_SNAKE_CASE convention (e.g., `START_CLAIM_EDUCATION_SCREEN`) used in other
 * EventTrackerScreenId enums. This is a legacy inconsistency that cannot be changed because:
 * 1. Screen IDs are sent to analytics as-is (using enum name)
 * 2. Historical analytics data in Snowflake uses these IDs
 * 3. Changing the names would break existing dashboards and queries
 *
 * New screen IDs added to this file should follow SCREAMING_SNAKE_CASE to align with other files,
 * but existing values must not be renamed.
 */
enum class InheritanceEventTrackerScreenId : EventTrackerScreenId {
  /**
   * Initial screen when starting an inheritance claim to explain the process to the user.
   */
  StartClaimEducationScreen,

  /**
   * Screen shown to the beneficiary confirming that they want to start a claim.
   */
  StartClaimConfirmationScreen,

  /**
   * Screen confirming that the user wants to open a claim.
   */
  SubmitClaimPromptScreen,

  /**
   * Loading screen while the app attempts to open a claim.
   */
  SubmittingClaim,

  /**
   * Screen shown when a claim fails to start
   */
  SubmittingClaimFailed,

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
