package build.wallet.grants

import build.wallet.logging.logError
import okio.Buffer

private const val SERIALIZATION_TAG = "GrantSerialization"

/**
 * Serializes the Grant into a ByteArray following the packed struct format:
 * - version: Byte (1 byte)
 * - serialized_request: ByteArray (90 bytes - the serialized GrantRequest)
 * - signature: ByteArray (64 bytes)
 *
 * Returns null if the Grant has fields with invalid lengths for serialization.
 */
fun Grant.serializeToPackedStruct(): ByteArray? {
  if (this.serializedRequest.size != SERIALIZED_GRANT_REQUEST_LENGTH) {
    logError(tag = SERIALIZATION_TAG) {
      "Grant serialization failed: serializedRequest length is ${this.serializedRequest.size}, expected $SERIALIZED_GRANT_REQUEST_LENGTH"
    }
    return null
  }
  if (this.signature.size != GRANT_SIGNATURE_LEN) {
    logError(tag = SERIALIZATION_TAG) {
      "Grant serialization failed: signature length is ${this.signature.size}, expected $GRANT_SIGNATURE_LEN"
    }
    return null
  }

  val buffer = Buffer()

  buffer.writeByte(this.version.toInt())
  buffer.write(this.serializedRequest)
  buffer.write(this.signature)

  return buffer.readByteArray()
}
