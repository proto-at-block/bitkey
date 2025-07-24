package build.wallet.analytics.events.screen.id

enum class TxVerificationEventTrackerScreenId : EventTrackerScreenId {
  /**
   * Critical error screen shown if we can't load a policy locally.
   */
  POLICY_LOAD_FAILURE,

  /**
   * Main screen for managing and viewing the state of the transaction verification policy.
   */
  MANAGE_POLICY,

  /**
   * Screen that lets the user choose between 'always' and 'above amount' type policies.
   */
  CHOOSE_TX_POLICY_TYPE_SHEET,

  /**
   * Screen that lets the user specify a custom threshold amount for the policy.
   */
  SET_AMOUNT,

  /**
   * Loading screen shown while changes are being made to the policy.
   */
  UPDATING_POLICY,

  /**
   * Info/Confirmation screen shown when the user is told their transaction requires verification.
   */
  VERIFICATION_START,

  /**
   * Cancellation screen shown when the user rejects the verification as invalid.
   */
  VERIFICATION_REJECTED,

  /**
   * Cancellation screen shown when the user fails to complete the verification in time.
   */
  VERIFICATION_EXPIRED,

  /**
   * Unexpected problem when attempting to verify a transaction.
   */
  VERIFICATION_ERROR,
}
