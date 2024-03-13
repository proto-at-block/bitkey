package build.wallet.onboarding

import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Result

/**
 * Dao for storing hardware keys to use during onboarding.
 */
interface OnboardingKeyboxHardwareKeysDao {
  suspend fun get(): Result<OnboardingKeyboxHardwareKeys?, DbTransactionError>

  suspend fun set(keys: OnboardingKeyboxHardwareKeys): Result<Unit, DbTransactionError>

  suspend fun clear()
}
