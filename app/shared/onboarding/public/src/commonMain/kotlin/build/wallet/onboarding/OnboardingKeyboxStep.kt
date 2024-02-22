package build.wallet.onboarding

/**
 * Steps in onboarding that occur after the HW onboarding
 * and creation of the keybox.
 */
enum class OnboardingKeyboxStep {
  /**
   * STEP 1
   *
   * The step of creating and saving a backup of the keybox and account
   * to the customer's cloud storage.
   *
   * Marked as complete when the storage successfully saves or encounters
   * an unexpected error, or if opted to skip in dev builds.
   */
  CloudBackup,

  /**
   * STEP 2
   *
   * The step of setting up notifications – push, sms and email.
   *
   * Marked as complete when all channels are acknowledged in some way
   * (skipped or completed), and at least one of (sms, email) was completed.
   */
  NotificationPreferences,

  /**
   * STEP 3
   *
   * The step of setting up currency preference for fiat (and BTC - TODO(W-4764))
   *
   * Marked as complete when customer taps 'Done' on the preference screen.
   */
  CurrencyPreference,
}
