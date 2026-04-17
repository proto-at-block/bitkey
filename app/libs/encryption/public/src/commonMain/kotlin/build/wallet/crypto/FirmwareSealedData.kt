package build.wallet.crypto

private const val LENGTH_DELIMITED_WIRE_TYPE = 2
private const val DATA_FIELD_NUMBER = 1
private const val NONCE_FIELD_NUMBER = 2
private const val TAG_FIELD_NUMBER = 3

private const val SEALED_DATA_LENGTH = 32
private const val SEALED_NONCE_LENGTH = 12
private const val SEALED_TAG_LENGTH = 16

fun SealedData.firmwareSealedDataValidationError(): String? {
  val encodedBytes = toByteArray()
  val fieldLengths = mutableMapOf<Int, Int>()
  var offset = 0

  while (offset < encodedBytes.size) {
    val key = readVarint(encodedBytes, offset) ?: return "malformed protobuf field key"
    offset = key.nextOffset

    val fieldNumber = (key.value ushr 3).toInt()
    val wireType = (key.value and 0x07).toInt()
    if (wireType != LENGTH_DELIMITED_WIRE_TYPE) {
      return "unexpected wire type $wireType for field $fieldNumber"
    }

    val length = readVarint(encodedBytes, offset) ?: return "malformed protobuf length for field $fieldNumber"
    offset = length.nextOffset

    if (length.value < 0 || offset + length.value > encodedBytes.size) {
      return "field $fieldNumber has invalid length ${length.value}"
    }

    if (fieldNumber in DATA_FIELD_NUMBER..TAG_FIELD_NUMBER) {
      if (fieldLengths.put(fieldNumber, length.value.toInt()) != null) {
        return "duplicate field $fieldNumber"
      }
    }

    offset += length.value.toInt()
  }

  return when {
    fieldLengths[DATA_FIELD_NUMBER] != SEALED_DATA_LENGTH ->
      "data length must be $SEALED_DATA_LENGTH bytes"
    fieldLengths[NONCE_FIELD_NUMBER] != SEALED_NONCE_LENGTH ->
      "nonce length must be $SEALED_NONCE_LENGTH bytes"
    fieldLengths[TAG_FIELD_NUMBER] != SEALED_TAG_LENGTH ->
      "tag length must be $SEALED_TAG_LENGTH bytes"
    else -> null
  }
}

private data class VarintResult(
  val value: Long,
  val nextOffset: Int,
)

private fun readVarint(
  bytes: ByteArray,
  startOffset: Int,
): VarintResult? {
  var value = 0L
  var shift = 0
  var offset = startOffset

  while (offset < bytes.size && shift < 64) {
    val byte = bytes[offset].toInt() and 0xFF
    value = value or ((byte and 0x7F).toLong() shl shift)
    offset += 1

    if ((byte and 0x80) == 0) {
      return VarintResult(value = value, nextOffset = offset)
    }

    shift += 7
  }

  return null
}
