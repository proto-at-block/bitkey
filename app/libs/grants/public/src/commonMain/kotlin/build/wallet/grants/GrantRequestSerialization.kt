package build.wallet.grants

import build.wallet.logging.logError
import okio.Buffer

private const val SERIALIZATION_TAG = "GrantSerialization"

/**
 * Serializes the GrantRequest into a ByteArray following the packed struct format:
 * - version: Byte (1 byte)
 * - device_id: ByteArray (8 bytes)
 * - challenge: ByteArray (16 bytes)
 * - action: GrantAction (1 byte: 1 for FINGERPRINT_RESET, 2 for TRANSACTION_VERIFICATION)
 * - signature: ByteArray (64 bytes)
 *
 * Returns null if the GrantRequest has fields with invalid lengths for serialization.
 */
fun GrantRequest.serializeToPackedStruct(): ByteArray? {
  if (this.deviceId.size != GRANT_DEVICE_ID_LEN) {
    logError(tag = SERIALIZATION_TAG) {
      "GrantRequest serialization failed: deviceId length is ${this.deviceId.size}, expected $GRANT_DEVICE_ID_LEN"
    }
    return null
  }
  if (this.challenge.size != GRANT_CHALLENGE_LEN) {
    logError(tag = SERIALIZATION_TAG) {
      "GrantRequest serialization failed: challenge length is ${this.challenge.size}, expected $GRANT_CHALLENGE_LEN"
    }
    return null
  }
  if (this.signature.size != GRANT_SIGNATURE_LEN) {
    logError(tag = SERIALIZATION_TAG) {
      "GrantRequest serialization failed: signature length is ${this.signature.size}, expected $GRANT_SIGNATURE_LEN"
    }
    return null
  }

  val buffer = Buffer()

  buffer.writeByte(this.version.toInt())
  buffer.write(this.deviceId)
  buffer.write(this.challenge)
  buffer.writeByte(this.action.value)
  buffer.write(this.signature)

  return buffer.readByteArray()
}
