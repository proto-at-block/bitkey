package build.wallet.statemachine.data.nfc

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.nfc.NfcManagerError

/**
 * Represents data types for executing NFC commands.
 *
 * "NFC Command" in our case usually means one or multiple WCAs (Wallet Custom APDUs) that
 * are executed over NFC.
 */
sealed interface NfcCommandData {
  /**
   * Describes context in which NFC command is executed.
   */
  val eventTrackerContext: NfcEventTrackerScreenIdContext?

  /**
   * Indicates that NFC capability is not available on this device.
   * This is applicable only for Android devices and the JVM platform.
   *
   * Although we've configured our Google Play Store listing (via `app/AndroidManifest.xml`) to
   * prevent installation on devices that lack NFC capability, this class state to handle any
   * potential edge cases. Such edge cases may arise, for example, with obscure Android vendors that
   * manage to bypass the NFC capability checks in the Google Play Store.
   */
  data class NfcNotAvailableData(
    override val eventTrackerContext: NfcEventTrackerScreenIdContext?,
  ) : NfcCommandData

  /**
   * Indicates that NFC capability is available but disabled on this device in system settings.
   * This is applicable only for Android devices.
   *
   * @param checkNfcAvailability a callback to check NFC availability again, hopefully, after
   * customer enabled NFC in device system settings.
   */
  data class NfcDisabledData(
    override val eventTrackerContext: NfcEventTrackerScreenIdContext?,
    val checkNfcAvailability: () -> Unit,
  ) : NfcCommandData

  /**
   * Indicates that we are in process of trying to execute an NFC command.
   * @property isTagConnected: Distinguishes if the tag is currently connected or not. If not,
   * we are searching for a tag.
   */
  data class ExecutingNfcCommandData(
    override val eventTrackerContext: NfcEventTrackerScreenIdContext?,
    val isTagConnected: Boolean,
  ) : NfcCommandData

  /**
   * Indicates that we have successfully executed an NFC command.
   *
   * @param proceed a callback to move this state machine forward after an NFC command has
   * successfully executed. This exists to give UI a chance to display success screen for a short
   * period of time before proceeding to next state.
   */
  data class NfcCommandExecutedData(
    override val eventTrackerContext: NfcEventTrackerScreenIdContext?,
    val proceed: () -> Unit,
  ) : NfcCommandData

  /**
   * Indicates that an error occurred during command execution.
   */
  data class NfcCommandErrorData(
    override val eventTrackerContext: NfcEventTrackerScreenIdContext,
    val error: NfcManagerError,
  ) : NfcCommandData
}
