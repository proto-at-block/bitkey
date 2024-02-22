package build.wallet.account

import build.wallet.bitkey.account.Account
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
  /**
   * Returns current Account status:
   * - there's an active account (Full or Lite)
   * - there's an onboarding account (Full or Lite)
   * - there's no account status ("logged out" state)
   */
  fun accountStatus(): Flow<Result<AccountStatus, Error>>

  suspend fun setActiveAccount(account: Account): Result<Unit, Error>

  /**
   * Saves the given account to the db and sets it as the onboarding account.
   */
  suspend fun saveAccountAndBeginOnboarding(account: Account): Result<Unit, Error>

  suspend fun clear(): Result<Unit, Error>
}
