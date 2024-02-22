package build.wallet.emergencyaccesskit

import com.github.michaelbull.result.Result
import okio.ByteString

/**
 * Encode or decode the [EmergencyAccessKitPayload]. Encoded into base58.
 */
interface EmergencyAccessKitPayloadDecoder {
  /**
   * Encode an [EmergencyAccessKitPayload] to the serialized representation
   * and then store it in base58.
   */
  fun encode(payload: EmergencyAccessKitPayload): String

  /**
   * Decode a base58 serialized [EmergencyAccessKitPayload]
   * @return An [EmergencyAccessKitPayload] or
   * - [DecodeError.InvalidBase58Data] If the input is not a valid base58 string
   * - [DecodeError.InvalidProtoData] If the serialized protobuf is missing any fields
   * - [DecodeError.InvalidBackupVersion] If the serialized payload does not include the
   * supported backup version.
   */
  fun decode(encodedString: String): Result<EmergencyAccessKitPayload, DecodeError>

  /**
   * Encode the [EmergencyAccessKitBackupV1] to the protobuf representation.
   * The resulting [ByteString] should be encrypted by the CSEK before being
   * added to the [EmergencyAccessKitPayload]
   */
  fun encodeBackup(backupV1: EmergencyAccessKitBackup): ByteString

  /**
   * Decode the decrypted active spending keys from an [EmergencyAccessKitPayload]
   * @return a [DecodeError.InvalidProtoData] if any fields are missing
   */
  fun decodeDecryptedBackup(keysetData: ByteString): Result<EmergencyAccessKitBackup, DecodeError>

  sealed class DecodeError : Error() {
    /** The data was not a valid base58 string */
    data class InvalidBase58Data(override val cause: Throwable?) : DecodeError()

    /** The serialized protobuf was missing one or more fields */
    data class InvalidProtoData(override val cause: Throwable? = null) : DecodeError()

    /** The backup version in this payload is not a supported version */
    data object InvalidBackupVersion : DecodeError()
  }
}
