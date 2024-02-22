package build.wallet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString

class BytesTests : FunSpec({
  val unsignedBytesList: List<UByte> = (0..255).map { it.toUByte() }
  val signedBytesArray: Array<Byte> = (0..255).map { it.toByte() }.toTypedArray()

  val byteArray = signedBytesArray.toByteArray()
  val byteString = ByteString.of(*byteArray)

  val emptyUnsignedBytesList: List<UByte> = emptyList()
  val emptyByteArray = ByteArray(0)
  val emptyByteString = ByteString.EMPTY

  context("List<UByte> to ByteArray") {
    test("convert empty") {
      emptyUnsignedBytesList.toByteArray().shouldBe(emptyByteArray)
    }

    test("convert non-empty bytes") {
      unsignedBytesList.toByteArray().shouldBe(byteArray)
    }

    test("internal consistency") {
      unsignedBytesList.toByteArray().toUByteList().shouldBe(unsignedBytesList)
    }
  }

  context("List<UByte to ByteString") {
    test("convert empty") {
      emptyUnsignedBytesList.toByteString().shouldBe(emptyByteString)
    }

    test("convert non-empty bytes") {
      unsignedBytesList.toByteString().shouldBe(byteString)
    }

    test("internal consistency") {
      unsignedBytesList.toByteString().toUByteList().shouldBe(unsignedBytesList)
    }
  }

  context("ByteArray to List<UByte>") {
    test("convert empty") {
      emptyByteArray.toUByteList().shouldBe(emptyUnsignedBytesList)
    }

    test("convert non-empty bytes") {
      byteArray.toUByteList().shouldBe(unsignedBytesList)
    }

    test("internal consistency") {
      byteArray.toUByteList().toByteArray().shouldBe(byteArray)
    }
  }

  context("ByteString to List<UByte>") {
    test("convert empty") {
      emptyByteString.toUByteList().shouldBe(emptyUnsignedBytesList)
    }

    test("convert non-empty bytes") {
      byteString.toUByteList().shouldBe(unsignedBytesList)
    }

    test("internal consistency") {
      byteString.toUByteList().toByteString().shouldBe(byteString)
    }
  }
})
