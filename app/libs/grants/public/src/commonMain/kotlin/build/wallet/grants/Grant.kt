package build.wallet.grants

import build.wallet.logging.logError
import okio.Buffer
import okio.ByteString

private const val SERIALIZATION_TAG = "GrantSerialization"

/**
 * A message signed by WSM indicating authorization for the requested action.
 * - version: Byte (1 byte)
 * - serialized_request: ByteArray (90 bytes)
 * - app_signature: ByteArray (64 bytes) - App signature over request core + label
 * - wsm_signature: ByteArray (64 bytes) - WSM Integrity Key signature over version + serialized_request + app_signature + label
 */
data class Grant(
  val version: Byte,
  val serializedRequest: ByteArray,
  val appSignature: ByteArray,
  val wsmSignature: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return other is Grant &&
      version == other.version &&
      serializedRequest.contentEquals(other.serializedRequest) &&
      appSignature.contentEquals(other.appSignature) &&
      wsmSignature.contentEquals(other.wsmSignature)
  }

  override fun hashCode(): Int {
    return arrayOf(
      version,
      serializedRequest.contentHashCode(),
      appSignature.contentHashCode(),
      wsmSignature.contentHashCode()
    ).contentHashCode()
  }

  /**
   * Serializes the Grant into a ByteString following the packed struct format:
   * - version: Byte (1 byte)
   * - serialized_request: ByteArray (90 bytes)
   * - app_signature: ByteArray (64 bytes)
   * - wsm_signature: ByteArray (64 bytes)
   *
   * Returns null if the Grant has fields with invalid lengths for serialization.
   */
  fun toBytes(): ByteString? {
    if (serializedRequest.size != SERIALIZED_GRANT_REQUEST_LENGTH) {
      logError(tag = SERIALIZATION_TAG) {
        "serializedRequest length is ${serializedRequest.size}, expected $SERIALIZED_GRANT_REQUEST_LENGTH"
      }
      return null
    }
    if (appSignature.size != GRANT_SIGNATURE_LEN) {
      logError(tag = SERIALIZATION_TAG) {
        "appSignature length is ${appSignature.size}, expected $GRANT_SIGNATURE_LEN"
      }
      return null
    }
    if (wsmSignature.size != GRANT_SIGNATURE_LEN) {
      logError(tag = SERIALIZATION_TAG) {
        "wsmSignature length is ${wsmSignature.size}, expected $GRANT_SIGNATURE_LEN"
      }
      return null
    }

    val buffer = Buffer()
    buffer.writeByte(version.toInt())
    buffer.write(serializedRequest)
    buffer.write(appSignature)
    buffer.write(wsmSignature)

    return buffer.readByteString()
  }

  /**
   * Determines the GrantAction from the Grant's serialized request.
   */
  fun getGrantAction(): GrantAction {
    val expectedLength = GRANT_VERSION_LEN + GRANT_DEVICE_ID_LEN + GRANT_CHALLENGE_LEN + GRANT_ACTION_LEN + GRANT_SIGNATURE_LEN
    require(serializedRequest.size == expectedLength) {
      "Invalid serialized request length: ${serializedRequest.size}, expected $expectedLength"
    }

    // Action byte is at position: version(1) + deviceId(8) + challenge(16) = 25
    val actionByte = serializedRequest[ACTION_BYTE_INDEX]

    return when (actionByte.toInt()) {
      GrantAction.FINGERPRINT_RESET.value -> GrantAction.FINGERPRINT_RESET
      GrantAction.TRANSACTION_VERIFICATION.value -> GrantAction.TRANSACTION_VERIFICATION
      else -> error("Unknown grant action byte: $actionByte")
    }
  }
}
