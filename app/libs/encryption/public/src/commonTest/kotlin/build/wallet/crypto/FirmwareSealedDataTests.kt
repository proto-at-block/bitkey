package build.wallet.crypto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.toByteString

class FirmwareSealedDataTests : FunSpec({
  test("valid sealed data passes validation") {
    sealedData(
      dataLength = 32,
      nonceLength = 12,
      tagLength = 16
    ).firmwareSealedDataValidationError().shouldBeNull()
  }

  test("invalid data length fails validation") {
    sealedData(
      dataLength = 31,
      nonceLength = 12,
      tagLength = 16
    ).firmwareSealedDataValidationError() shouldBe "data length must be 32 bytes"
  }

  test("invalid nonce length fails validation") {
    sealedData(
      dataLength = 32,
      nonceLength = 11,
      tagLength = 16
    ).firmwareSealedDataValidationError() shouldBe "nonce length must be 12 bytes"
  }

  test("invalid tag length fails validation") {
    sealedData(
      dataLength = 32,
      nonceLength = 12,
      tagLength = 15
    ).firmwareSealedDataValidationError() shouldBe "tag length must be 16 bytes"
  }

  test("duplicate field fails validation") {
    buildList {
      addAll(lengthDelimitedField(fieldNumber = 1, length = 32))
      addAll(lengthDelimitedField(fieldNumber = 1, length = 32))
      addAll(lengthDelimitedField(fieldNumber = 2, length = 12))
      addAll(lengthDelimitedField(fieldNumber = 3, length = 16))
    }.toByteArray().toByteString()
      .firmwareSealedDataValidationError() shouldBe "duplicate field 1"
  }

  test("unexpected wire type fails validation") {
    byteArrayOf(
      ((1 shl 3) or 0).toByte(), // field 1, varint wire type instead of length-delimited
      0x01
    ).toByteString().firmwareSealedDataValidationError() shouldBe "unexpected wire type 0 for field 1"
  }
})

private fun sealedData(
  dataLength: Int,
  nonceLength: Int,
  tagLength: Int,
) = buildList {
  addAll(lengthDelimitedField(fieldNumber = 1, length = dataLength))
  addAll(lengthDelimitedField(fieldNumber = 2, length = nonceLength))
  addAll(lengthDelimitedField(fieldNumber = 3, length = tagLength))
}.toByteArray().toByteString()

private fun lengthDelimitedField(
  fieldNumber: Int,
  length: Int,
): List<Byte> {
  val key = (fieldNumber shl 3) or 2
  return buildList {
    addAll(encodeVarint(key))
    addAll(encodeVarint(length))
    repeat(length) { add(0) }
  }.map(Int::toByte)
}

private fun encodeVarint(value: Int): List<Int> {
  var remaining = value
  val bytes = mutableListOf<Int>()

  do {
    var current = remaining and 0x7F
    remaining = remaining ushr 7
    if (remaining != 0) {
      current = current or 0x80
    }
    bytes += current
  } while (remaining != 0)

  return bytes
}
