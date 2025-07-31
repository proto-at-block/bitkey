package build.wallet.grants

import build.wallet.logging.logError
import okio.Buffer
import okio.ByteString

private const val SERIALIZATION_TAG = "GrantSerialization"

/**
 * A message signed by WSM indicating authorization for the requested action.
 * - version: Byte (1 byte)
 * - serialized_request: ByteArray (90 bytes)
 * - signature: ByteArray (64 bytes)
 */
data class Grant(
  val version: Byte,
  val serializedRequest: ByteArray,
  val signature: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return other is Grant &&
      version == other.version &&
      serializedRequest.contentEquals(other.serializedRequest) &&
      signature.contentEquals(other.signature)
  }

  override fun hashCode(): Int {
    return arrayOf(
      version,
      serializedRequest.contentHashCode(),
      signature.contentHashCode()
    ).contentHashCode()
  }

  /**
   * Serializes the Grant into a ByteString following the packed struct format:
   * - version: Byte (1 byte)
   * - serialized_request: ByteArray (90 bytes)
   * - signature: ByteArray (64 bytes)
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
    if (signature.size != GRANT_SIGNATURE_LEN) {
      logError(tag = SERIALIZATION_TAG) {
        "signature length is ${signature.size}, expected $GRANT_SIGNATURE_LEN"
      }
      return null
    }

    val buffer = Buffer()
    buffer.writeByte(version.toInt())
    buffer.write(serializedRequest)
    buffer.write(signature)

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
