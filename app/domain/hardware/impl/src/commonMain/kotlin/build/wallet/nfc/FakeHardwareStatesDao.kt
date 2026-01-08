package build.wallet.nfc

import build.wallet.db.DbError
import com.github.michaelbull.result.Result

/**
 * DAO for managing fake hardware state used for testing.
 */
interface FakeHardwareStatesDao {
  /**
   * Set whether Transaction Verification is enabled on the fake hardware.
   *
   * @param enabled True if Transaction Verification should be enabled, false otherwise.
   * @return A result indicating success or failure.
   */
  suspend fun setTransactionVerificationEnabled(enabled: Boolean): Result<Unit, DbError>

  /**
   * Get the current Transaction Verification enabled state.
   *
   * @return The enabled state if it exists, or null if not set.
   */
  suspend fun getTransactionVerificationEnabled(): Result<Boolean?, DbError>

  /**
   * Clear all fake hardware state from the database.
   *
   * @return A result indicating success or failure.
   */
  suspend fun clear(): Result<Unit, DbError>
}
