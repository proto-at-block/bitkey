package build.wallet.onboarding

import build.wallet.cloud.backup.csek.SealedSsek
import com.github.michaelbull.result.Result

/**
 * A temporary dao for storing the sealed SSEK in order to
 * resume onboarding from the descriptor backup step.
 */
interface OnboardingKeyboxSealedSsekDao {
  /**
   * Access [SealedSsek] from local storage, if available.
   */
  suspend fun get(): Result<SealedSsek?, Throwable>

  /**
   * Set sealed SSEK in local storage.
   */
  suspend fun set(value: SealedSsek): Result<Unit, Throwable>

  /**
   * Clears the sealed SSEK local storage.
   */
  suspend fun clear(): Result<Unit, Throwable>
}
