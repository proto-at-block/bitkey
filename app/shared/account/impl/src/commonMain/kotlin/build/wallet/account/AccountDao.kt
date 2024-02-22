package build.wallet.account

import build.wallet.bitkey.account.Account
import build.wallet.db.DbError
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
  fun activeAccount(): Flow<Result<Account?, DbError>>

  /**
   * Emits currently onboarding [Account]. If `null`, indicates that either there's an active
   * account, an account recovering, or there's no account data at all (logged out state).
   */
  fun onboardingAccount(): Flow<Result<Account?, DbError>>

  /**
   * Set an account as active.
   */
  suspend fun setActiveAccount(account: Account): Result<Unit, DbError>

  /**
   * Saves the given account to the db and sets it as the onboarding account.
   */
  suspend fun saveAccountAndBeginOnboarding(account: Account): Result<Unit, DbError>

  suspend fun clear(): Result<Unit, DbError>
}
