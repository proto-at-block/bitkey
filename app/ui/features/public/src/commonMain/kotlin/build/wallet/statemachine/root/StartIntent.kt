package build.wallet.statemachine.root

/**
 * Navigation intent used to inform the destination of the getting
 * started experience.
 */
enum class StartIntent {
  /**
   * Indicates that the user started onboarding with the intent to
   * restore an existing Bitkey account.
   */
  RestoreBitkey,

  /**
   * Indicates that the user started onboarding with the intent to
   * become a trusted contact.
   */
  BeTrustedContact,

  /**
   * Indicates onboarding was started with the intent to become a beneficiary.
   */
  BeBeneficiary,
}
