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
   * The step of setting up recovery methods and notifications – push, sms and email.
   *
   * After recovery channels, select available channels for notifications.
   */
  NotificationPreferences,
}
