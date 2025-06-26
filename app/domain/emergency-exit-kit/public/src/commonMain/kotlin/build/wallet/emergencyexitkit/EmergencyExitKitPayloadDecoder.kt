package build.wallet.emergencyexitkit

import com.github.michaelbull.result.Result
import okio.ByteString

/**
 * Encode or decode the [EmergencyExitKitPayload]. Encoded into base58.
 */
interface EmergencyExitKitPayloadDecoder {
  /**
   * Encode an [EmergencyExitKitPayload] to the serialized representation
   * and then store it in base58.
   */
  suspend fun encode(payload: EmergencyExitKitPayload): String

  /**
   * Decode a base58 serialized [EmergencyExitKitPayload]
   * @return An [EmergencyExitKitPayload] or
   * - [DecodeError.InvalidBase58Data] If the input is not a valid base58 string
   * - [DecodeError.InvalidProtoData] If the serialized protobuf is missing any fields
   * - [DecodeError.InvalidBackupVersion] If the serialized payload does not include the
   * supported backup version.
   */
  suspend fun decode(encodedString: String): Result<EmergencyExitKitPayload, DecodeError>

  /**
   * Encode the [EmergencyExitKitBackupV1] to the protobuf representation.
   * The resulting [ByteString] should be encrypted by the CSEK before being
   * added to the [EmergencyExitKitPayload]
   */
  suspend fun encodeBackup(backupV1: EmergencyExitKitBackup): ByteString

  /**
   * Decode the decrypted active spending keys from an [EmergencyExitKitPayload]
   * @return a [DecodeError.InvalidProtoData] if any fields are missing
   */
  suspend fun decodeDecryptedBackup(
    keysetData: ByteString,
  ): Result<EmergencyExitKitBackup, DecodeError>

  sealed class DecodeError : Error() {
    /** The data was not a valid base58 string */
    data class InvalidBase58Data(override val cause: Throwable?) : DecodeError()

    /** The serialized protobuf was missing one or more fields */
    data class InvalidProtoData(override val cause: Throwable? = null) : DecodeError()

    /** The backup version in this payload is not a supported version */
    data object InvalidBackupVersion : DecodeError()
  }
}
