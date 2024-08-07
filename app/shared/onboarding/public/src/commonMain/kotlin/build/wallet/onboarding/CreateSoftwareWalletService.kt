package build.wallet.onboarding

import build.wallet.bitkey.account.Account
import com.github.michaelbull.result.Result

/**
 * Service responsible for orchestrating the creation of an account for a software wallet.
 * This includes creating new app authentication and spending keys, creating
 * the account on the server, and activating the account locally.
 */
interface CreateSoftwareWalletService {
  /**
   * Creates and activates a new [Account] for a software wallet.
   */
  suspend fun createAccount(): Result<Account, Throwable>
}
