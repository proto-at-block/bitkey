package build.wallet.fwup

import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.McuRole
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.UpToDate
import kotlinx.collections.immutable.ImmutableList

/**
 * Describes the state of firmware for the currently activated keybox.
 *
 * @property firmwareUpdateState: The update state of the firmware
 * @property firmwareDeviceInfo: The device info of the firmware, null when no info has been stored
 */
data class FirmwareData(
  val firmwareUpdateState: FirmwareUpdateState,
  val firmwareDeviceInfo: FirmwareDeviceInfo?,
) {
  /**
   * Returns the version of the first MCU update if pending, or null if up to date.
   * For W1, this is the only MCU. For W3, this is typically CORE (first in list).
   */
  val updateVersion: String? =
    when (firmwareUpdateState) {
      is UpToDate -> null
      is PendingUpdate -> firmwareUpdateState.mcuUpdates.firstOrNull()?.version
    }

  /**
   * Returns true if multiple MCUs need updating (W3+).
   * For W1, this is always false (single MCU).
   */
  val hasMultipleMcus: Boolean =
    when (firmwareUpdateState) {
      is UpToDate -> false
      is PendingUpdate -> firmwareUpdateState.mcuUpdates.size > 1
    }

  sealed interface FirmwareUpdateState {
    /** The firmware is up to date with the latest available version.  */
    data object UpToDate : FirmwareUpdateState

    /**
     * The firmware is out of date with the latest available version and needs an update.
     *
     * @property mcuUpdates: List of MCU firmware updates to apply. For W1 devices,
     *   this is a single-element list. For W3+ devices, this contains updates for
     *   CORE, UXC, etc. in the order they should be applied (CORE first).
     */
    data class PendingUpdate(
      val mcuUpdates: ImmutableList<McuFwupData>,
    ) : FirmwareUpdateState {
      init {
        require(mcuUpdates.isNotEmpty()) { "mcuUpdates cannot be empty" }
      }
    }
  }
}

/**
 * Per-MCU firmware update state for multi-MCU devices (W3).
 * Tracks the update state independently for each MCU (CORE, UXC).
 */
sealed interface McuUpdateState {
  /** MCU firmware is at the target version. */
  data object UpToDate : McuUpdateState

  /**
   * MCU needs an update but hasn't started yet.
   * @property data The firmware data to update this MCU with
   */
  data class PendingUpdate(val data: McuFwupData) : McuUpdateState

  /**
   * MCU update is currently in progress (supports resume after NFC disconnection).
   * @property data The firmware data being applied
   * @property sequenceId The last successfully transferred sequence ID
   */
  data class InProgress(val data: McuFwupData, val sequenceId: UInt) : McuUpdateState
}

/**
 * Checks if any MCU in the map has a pending or in-progress update.
 */
fun Map<McuRole, McuUpdateState>.hasAnyPendingUpdate(): Boolean =
  values.any { it !is McuUpdateState.UpToDate }

/**
 * Returns an ordered list of MCUs needing updates (CORE first, then UXC).
 * Only includes MCUs that are not up-to-date.
 */
fun Map<McuRole, McuUpdateState>.pendingMcuUpdates(): List<McuRole> =
  listOfNotNull(
    McuRole.CORE.takeIf { containsKey(it) && this[it] !is McuUpdateState.UpToDate },
    McuRole.UXC.takeIf { containsKey(it) && this[it] !is McuUpdateState.UpToDate }
  )
