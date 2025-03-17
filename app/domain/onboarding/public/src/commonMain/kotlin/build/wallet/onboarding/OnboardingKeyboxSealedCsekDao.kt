package build.wallet.onboarding

import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

/**
 * A temporary dao for storing the sealed CSEK in order to
 * resume onboarding from the cloud backup step.
 */
interface OnboardingKeyboxSealedCsekDao {
  /**
   * Access [SealedCsek] from local storage, if available.
   */
  suspend fun get(): Result<SealedCsek?, Throwable>

  /**
   * Set sealed CSEK in local storage.
   */
  suspend fun set(value: SealedCsek): Result<Unit, Throwable>

  /**
   * Clears the sealed CSEK local storage.
   */
  suspend fun clear(): Result<Unit, Throwable>
}
