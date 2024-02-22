package build.wallet.fwup

import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface FwupDataDao {
  /**
   * Returns a flow of the stored [FwupData], if any
   */
  fun fwupData(): Flow<Result<FwupData?, DbError>>

  /**
   * Stores the given [FwupData], overwriting any existing
   */
  suspend fun setFwupData(fwupData: FwupData): Result<Unit, DbError>

  /**
   * Clears any stored [FwupData]
   */
  suspend fun clear(): Result<Unit, DbError>
}
