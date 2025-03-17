package build.wallet.onboarding

import build.wallet.bitkey.account.SoftwareAccount
import com.github.michaelbull.result.Result

/**
 * Service responsible for orchestrating the creation of an account for a software wallet.
 * This includes creating new app authentication and spending keys, creating
 * the account on the server, and activating the account locally.
 */
interface OnboardSoftwareAccountService {
  /**
   * Creates and activates a new [SoftwareAccount] for a software wallet.
   */
  suspend fun createAccount(): Result<SoftwareAccount, Throwable>
}
