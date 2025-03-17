package build.wallet.account

import build.wallet.bitkey.account.Account
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Dao for managing account state.
 */
interface AccountDao {
  /**
   * Emits currently active [Account]. If `null`, indicates that either there's an onboarding
   * account, an account recovering, or there's no account data at all (logged out state).
   */
  fun activeAccount(): Flow<Result<Account?, Error>>

  /**
   * Emits currently onboarding [Account]. If `null`, indicates that either there's an active
   * account, an account recovering, or there's no account data at all (logged out state).
   */
  fun onboardingAccount(): Flow<Result<Account?, Error>>

  /**
   * Set an account as active.
   */
  suspend fun setActiveAccount(account: Account): Result<Unit, Error>

  /**
   * Saves the given account to the db and sets it as the onboarding account.
   */
  suspend fun saveAccountAndBeginOnboarding(account: Account): Result<Unit, Error>

  suspend fun clear(): Result<Unit, Error>
}
