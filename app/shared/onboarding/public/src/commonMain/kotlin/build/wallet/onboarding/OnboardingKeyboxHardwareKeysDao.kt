package build.wallet.onboarding

import com.github.michaelbull.result.Result

/**
 * Dao for storing hardware keys to use during onboarding.
 */
interface OnboardingKeyboxHardwareKeysDao {
  suspend fun get(): Result<OnboardingKeyboxHardwareKeys?, Error>

  suspend fun set(keys: OnboardingKeyboxHardwareKeys): Result<Unit, Error>

  suspend fun clear()
}
