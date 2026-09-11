package build.wallet.fwup

import build.wallet.firmware.McuRole
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface FwupDataDao {
  /**
   * Stores a list of [McuFwupData], each keyed by its MCU role, but only if the currently
   * paired device's serial is still [expectedSerial]. Returns true when the data was stored.
   *
   * Fetching firmware requires reading device info and then downloading over the network, so
   * the paired device can change while a fetch is in flight. Comparing the serial inside the
   * write transaction prevents a fetch that started before a hardware swap from repopulating
   * the cache with firmware for the previous device.
   */
  suspend fun setMcuFwupData(
    mcuFwupDataList: List<McuFwupData>,
    expectedSerial: String,
  ): Result<Boolean, Error>

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
