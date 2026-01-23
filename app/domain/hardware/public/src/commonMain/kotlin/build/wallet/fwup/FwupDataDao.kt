package build.wallet.fwup

import build.wallet.firmware.McuRole
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface FwupDataDao {
  /**
   * Stores a list of [McuFwupData], overwriting any existing data for those MCUs.
   * Each MCU is keyed by its role.
   */
  suspend fun setMcuFwupData(mcuFwupDataList: List<McuFwupData>): Result<Unit, Error>

  /**
   * Returns the stored [McuFwupData] for a specific MCU role, if any.
   */
  suspend fun getMcuFwupData(mcuRole: McuRole): Result<McuFwupData?, Error>

  /**
   * Returns all stored [McuFwupData] entries.
   */
  suspend fun getAllMcuFwupData(): Result<List<McuFwupData>, Error>

  /**
   * Clears all stored per-MCU firmware data.
   */
  suspend fun clearAllMcuFwupData(): Result<Unit, Error>

  /**
   * Clears stored firmware data for a specific MCU.
   */
  suspend fun clearMcuFwupData(mcuRole: McuRole): Result<Unit, Error>

  /**
   * Returns a flow of all stored [McuFwupData] entries for multi-MCU devices.
   * Emits an empty list if no data is stored.
   */
  fun mcuFwupData(): Flow<Result<List<McuFwupData>, Error>>

  /**
   * Clears any stored [FwupData]
   */
  suspend fun clear(): Result<Unit, Error>

  /**
   * Gets the sequence ID for a specific MCU (W3 multi-MCU support).
   * @throws NoSuchElementException if no sequence ID has been stored for this MCU.
   */
  suspend fun getMcuSequenceId(mcuRole: McuRole): Result<UInt, Error>

  /**
   * Sets the sequence ID for a specific MCU (W3 multi-MCU support).
   */
  suspend fun setMcuSequenceId(
    mcuRole: McuRole,
    sequenceId: UInt,
  ): Result<Unit, Error>

  /**
   * Clears all per-MCU state (sequence IDs).
   */
  suspend fun clearAllMcuStates(): Result<Unit, Error>
}
