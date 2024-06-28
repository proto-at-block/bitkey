package build.wallet.onboarding

import com.github.michaelbull.result.Result

/**
 * Workflow responsible for orchestrating the creation of an account for a software wallet.
 * This includes creating new app authentication and spending keys, creating
 * the account on the server, and activating the account locally.
 */
interface CreateSoftwareWalletWorkflow {
  /**
   * Creates and activates a new account for a software wallet.
   */
  suspend fun createAccount(): Result<Unit, Throwable>
}