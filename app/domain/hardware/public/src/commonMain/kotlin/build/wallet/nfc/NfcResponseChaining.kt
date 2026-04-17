package build.wallet.nfc

import kotlin.coroutines.cancellation.CancellationException

/**
 * WCA GET RESPONSE APDU: CLA=0x87, INS=0x78, P1=0x00, P2=0x00.
 *
 * Sent to the hardware to retrieve the next chunk of a response that was too
 * large to fit in a single APDU frame.
 */
private val WCA_GET_RESPONSE_APDU = listOf<UByte>(0x87u, 0x78u, 0x00u, 0x00u)

/**
 * ISO 7816-4 status byte indicating more response data is available.
 */
private val SW1_MORE_DATA = 0x61.toUByte()

/**
 * Safety cap on chained GET RESPONSE iterations. Each APDU chunk carries ~508
 * payload bytes, so 20 iterations covers responses up to ~10 KB — far beyond
 * any current WCA command. A misbehaving tag that keeps returning SW1=0x61
 * will hit this cap and fail fast instead of looping indefinitely.
 */
private const val MAX_CHAINED_RESPONSES = 20

/**
 * Sends an APDU buffer via NFC and handles ISO 7816-4 response chaining.
 *
 * NFC APDU responses have a 2-byte status trailer: `[...payload..., SW1, SW2]`.
 * When firmware has more data than fits in a single APDU frame (~510 bytes),
 * it signals this by returning SW1=0x61 ("more data available"). SW2 contains
 * the number of remaining bytes (informational; we don't rely on it).
 *
 * To retrieve the full response we:
 *   1. Strip SW1/SW2 from the partial response and accumulate the payload.
 *   2. Send a WCA GET RESPONSE command (CLA=0x87, INS=0x78, P1=0, P2=0).
 *   3. Repeat until SW1 != 0x61 (i.e. the final chunk).
 *   4. Append the final chunk (payload + SW1/SW2) to produce the complete
 *      response as if it had arrived in one piece.
 *
 * For responses that fit in a single APDU (the common case), this returns
 * the response from [NfcSession.transceive] unchanged.
 *
 * This is used in the transport layer so every WCA command — current and
 * future — gets response chaining automatically, without the Rust command
 * generators needing to know about it.
 */
@Throws(NfcException::class, CancellationException::class)
suspend fun NfcSession.transceiveWithChaining(buffer: List<UByte>): List<UByte> {
  var response = transceive(buffer)
  if (response.size < 2 || response[response.size - 2] != SW1_MORE_DATA) {
    return response
  }
  // First chunk had SW1=0x61 — accumulate payload (strip trailing SW1/SW2).
  val accumulated = response.dropLast(2).toMutableList()
  var chunksReceived = 0
  while (true) {
    chunksReceived++
    if (chunksReceived > MAX_CHAINED_RESPONSES) {
      throw NfcException.CommandError(
        cause = IllegalStateException(
          "Response chaining exceeded $MAX_CHAINED_RESPONSES iterations"
        )
      )
    }
    response = transceive(WCA_GET_RESPONSE_APDU)
    if (response.size < 2) {
      throw NfcException.CommandError(
        cause = IllegalStateException(
          "Malformed chained APDU response: expected at least 2 bytes (SW1/SW2), got ${response.size}"
        )
      )
    }
    if (response[response.size - 2] == SW1_MORE_DATA) {
      accumulated.addAll(response.dropLast(2)) // intermediate chunk payload
    } else {
      accumulated.addAll(response) // final chunk: payload + terminal SW1/SW2
      break
    }
  }
  return accumulated
}
