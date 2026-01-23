package build.wallet.nfc

import bitkey.account.HardwareType
import okio.ByteString
import kotlin.coroutines.cancellation.CancellationException

/**
 * [NfcSession], which is a wrapper around the platform's `Tag` object. It exposes:
 *
 *   - `transceive`, which sends bytes to the tag and returns a response;
 *      it will suspend until a tag is available
 *   - `message`, which displays a message on platform's native NFC UI (iOS-only)
 *   - `close` … self-explanatory
 *
 **/
interface NfcSession : AutoCloseable {
  var message: String?
  val parameters: Parameters

  @Throws(NfcException::class, CancellationException::class)
  suspend fun transceive(buffer: List<UByte>): List<UByte>

  /**
   * @param needsAuthentication: Whether or not the transaction requires the hardware to be unlocked.
   * This is used to determine UI copy shown to customer instructing them to unlock their hardware
   * before NFC tap.
   * @param shouldLock: Whether or not the hardware should be locked when the transaction completes
   * @param skipFirmwareTelemetry: Whether or not to skip shipping up firmware telemetry
   * @param asyncNfcSigning: Whether or not to use async NFC signing
   * @param isHardwareFake: Whether to use fake/simulated hardware
   * @param hardwareType: The type of hardware (W1 or W3) to use/simulate
   * @param checkHardwareIsPaired: Function to verify if a challenge signature was made by the paired hardware
   * @param requirePairedHardware: Whether to validate that the hardware being used is the one paired with the account
   * @param maxNfcRetryAttempts: Maximum number of retry attempts for NFC sessions that are invalidated unexpectedly
   */
  class Parameters(
    val isHardwareFake: Boolean,
    val hardwareType: HardwareType?,
    val needsAuthentication: Boolean,
    val shouldLock: Boolean,
    val skipFirmwareTelemetry: Boolean,
    val asyncNfcSigning: Boolean,
    val nfcFlowName: String,
    val requirePairedHardware: RequirePairedHardware,
    val maxNfcRetryAttempts: Int = 3,
    onTagConnected: (NfcSession?) -> Unit,
    onTagDisconnected: () -> Unit,
  ) {
    val onTagConnectedObservers = mutableListOf(onTagConnected)
    val onTagConnected: (NfcSession?) -> Unit =
      { session -> onTagConnectedObservers.forEach { it(session) } }

    val onTagDisconnectedObservers = mutableListOf(onTagDisconnected)
    val onTagDisconnected: () -> Unit = { onTagDisconnectedObservers.forEach { it() } }
  }

  /** Whether we should check that the tapped hardware matches that expected by the app. */
  sealed interface RequirePairedHardware {
    data object NotRequired : RequirePairedHardware

    data class Required(
      /** The challenge to be signed by hardware. */
      val challenge: ByteString,
      /** The callback in which the signature and challenge and verified, returning the result of the verification. */
      val checkHardwareIsPaired: (String, ByteString) -> Boolean,
    ) : RequirePairedHardware
  }
}
