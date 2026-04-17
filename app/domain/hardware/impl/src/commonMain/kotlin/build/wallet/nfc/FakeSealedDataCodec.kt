package build.wallet.nfc

import build.wallet.crypto.SealedData
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Shared protobuf-like codec for seal/unseal operations in fake hardware implementations.
 *
 * Both W1 and W3 fake commands use these helpers to encode/decode sealed data envelopes
 * that match firmware's field layout: data (field 1), nonce (field 2), tag (field 3).
 */
internal object FakeSealedDataCodec {
  fun buildSealedDataProto(
    data: ByteString,
    nonce: ByteString,
    tag: ByteString,
  ): SealedData =
    Buffer()
      .write(lengthDelimitedField(fieldNumber = 1, value = data))
      .write(lengthDelimitedField(fieldNumber = 2, value = nonce))
      .write(lengthDelimitedField(fieldNumber = 3, value = tag))
      .readByteString()

  fun parseSealedDataProto(sealedData: SealedData): ParsedSealedData {
    val bytes = sealedData.toByteArray()
    var offset = 0
    var data: ByteString? = null
    var nonce: ByteString? = null
    var tag: ByteString? = null

    while (offset < bytes.size) {
      val key = readVarint(bytes, offset)
      offset = key.nextOffset

      val fieldNumber = (key.value ushr 3).toInt()
      val wireType = (key.value and 0x07).toInt()
      require(wireType == 2) { "Unexpected wire type $wireType for field $fieldNumber" }

      val length = readVarint(bytes, offset)
      offset = length.nextOffset
      val lengthValue = length.value
      require(lengthValue >= 0) { "Negative field length $lengthValue for field $fieldNumber" }
      require(lengthValue <= Int.MAX_VALUE.toLong()) {
        "Field length $lengthValue for field $fieldNumber exceeds Int.MAX_VALUE"
      }

      val endOffsetLong = offset.toLong() + lengthValue
      require(endOffsetLong <= bytes.size.toLong()) {
        "Invalid field length $lengthValue for field $fieldNumber"
      }
      val endOffset = endOffsetLong.toInt()

      val value = bytes.copyOfRange(offset, endOffset).toByteString()
      when (fieldNumber) {
        1 -> data = value
        2 -> nonce = value
        3 -> tag = value
      }
      offset = endOffset
    }

    return ParsedSealedData(
      data = requireNotNull(data) { "Missing sealed data field" },
      nonce = requireNotNull(nonce) { "Missing nonce field" },
      tag = requireNotNull(tag) { "Missing tag field" }
    )
  }

  private fun lengthDelimitedField(
    fieldNumber: Int,
    value: ByteString,
  ): ByteString =
    Buffer()
      .write(encodeVarint(((fieldNumber shl 3) or 2).toLong()))
      .write(encodeVarint(value.size.toLong()))
      .write(value)
      .readByteString()

  private fun encodeVarint(value: Long): ByteString {
    var remaining = value
    val bytes = mutableListOf<Byte>()

    do {
      var nextByte = (remaining and 0x7f).toInt()
      remaining = remaining ushr 7
      if (remaining != 0L) {
        nextByte = nextByte or 0x80
      }
      bytes += nextByte.toByte()
    } while (remaining != 0L)

    return bytes.toByteArray().toByteString()
  }

  private fun readVarint(
    bytes: ByteArray,
    startOffset: Int,
  ): VarintResult {
    var value = 0L
    var shift = 0
    var offset = startOffset

    while (offset < bytes.size && shift < 64) {
      val byte = bytes[offset].toInt() and 0xff
      value = value or ((byte and 0x7f).toLong() shl shift)
      offset += 1

      if ((byte and 0x80) == 0) {
        return VarintResult(value = value, nextOffset = offset)
      }

      shift += 7
    }

    error("Malformed varint in sealed data")
  }

  /**
   * Seals data using a fake hardware key store's auth key as nonce/tag source.
   * Shared by both W1 and W3 fake command implementations.
   */
  suspend fun sealWithKeyStore(
    keyStore: FakeHardwareKeyStore,
    unsealedData: ByteString,
  ): SealedData {
    val authKeyBytes = keyStore.getAuthKeypair().privateKey.key.bytes
    val nonce = authKeyBytes.substring(0, 12)
    val tag = authKeyBytes.substring(12, 28)
    return buildSealedDataProto(data = unsealedData, nonce = nonce, tag = tag)
  }

  /**
   * Unseals data using a fake hardware key store's auth key for nonce/tag verification.
   * Shared by both W1 and W3 fake command implementations.
   */
  suspend fun unsealWithKeyStore(
    keyStore: FakeHardwareKeyStore,
    sealedData: SealedData,
  ): ByteString {
    val parsedSealedData = try {
      parseSealedDataProto(sealedData)
    } catch (_: RuntimeException) {
      throw NfcException.CommandErrorSealCsekResponseUnsealException()
    }
    val authKeyBytes = keyStore.getAuthKeypair().privateKey.key.bytes
    val expectedNonce = authKeyBytes.substring(0, 12)
    val expectedTag = authKeyBytes.substring(12, 28)

    if (parsedSealedData.nonce != expectedNonce || parsedSealedData.tag != expectedTag) {
      throw NfcException.CommandErrorSealCsekResponseUnsealException()
    }
    return parsedSealedData.data
  }

  data class ParsedSealedData(
    val data: ByteString,
    val nonce: ByteString,
    val tag: ByteString,
  )

  private data class VarintResult(
    val value: Long,
    val nextOffset: Int,
  )
}
