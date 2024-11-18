package build.wallet.fwup

import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface FwupDataDao {
  /**
   * Returns a flow of the stored [FwupData], if any
   */
  fun fwupData(): Flow<Result<FwupData?, Error>>

  /**
   * Stores the given [FwupData], overwriting any existing
   */
  suspend fun setFwupData(fwupData: FwupData): Result<Unit, Error>

  /**
   * Clears any stored [FwupData]
   */
  suspend fun clear(): Result<Unit, Error>

  suspend fun setSequenceId(sequenceId: UInt): Result<Unit, Error>

  suspend fun getSequenceId(): Result<UInt, Error>
}
