package build.wallet.nfc

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
   * @param needsAuthentication: Whether or not the transaction requires the hardware to be unlocked
   * @param shouldLock: Whether or not the hardware should be locked when the transaction completes
   * @param skipFirmwareTelemetry: Whether or not to skip shipping up firmware telemetry
   * @param asyncNfcSigning: Whether or not to use async NFC signing
   */
  class Parameters(
    val isHardwareFake: Boolean,
    val needsAuthentication: Boolean,
    val shouldLock: Boolean,
    val skipFirmwareTelemetry: Boolean,
    val asyncNfcSigning: Boolean,
    val nfcFlowName: String,
    onTagConnected: (NfcSession?) -> Unit,
    onTagDisconnected: () -> Unit,
  ) {
    val onTagConnectedObservers = mutableListOf(onTagConnected)
    val onTagConnected: (NfcSession?) -> Unit =
      { session -> onTagConnectedObservers.forEach { it(session) } }

    val onTagDisconnectedObservers = mutableListOf(onTagDisconnected)
    val onTagDisconnected: () -> Unit = { onTagDisconnectedObservers.forEach { it() } }
  }
}
