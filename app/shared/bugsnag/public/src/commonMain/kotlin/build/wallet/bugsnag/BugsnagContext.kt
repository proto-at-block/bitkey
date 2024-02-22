package build.wallet.bugsnag

interface BugsnagContext {
  /**
   * Configures the Bugsnag context with necessary metadata.
   *
   * This should be called after Bugsnag is initialized.
   */
  fun configureCommonMetadata()
}
