package build.wallet.serialization

import com.github.michaelbull.result.getOrThrow
import com.ionspin.kotlin.bignum.integer.BigInteger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

class Base10Tests : FunSpec({
  test("encoding a ByteString should produce the expected base-10 string") {
    val byteString = "7B".decodeHex()
    val result = Base10.encode(byteString).getOrThrow()
    result shouldBeEqual "123"

    val emptyByteString = ByteString.EMPTY
    val emptyResult = Base10.encode(emptyByteString).getOrThrow()
    emptyResult shouldBeEqual "0"
  }

  test("decoding a base-10 string to ByteString should match the expected hex representation") {
    val base10String = "123"
    val result = Base10.decode(base10String).getOrThrow()
    result shouldBeEqual "7B".decodeHex()

    val zeroString = "0"
    val zeroResult = Base10.decode(zeroString).getOrThrow()
    zeroResult shouldBeEqual ByteString.EMPTY

    val leadingZeroString = "0123"
    val leadingZeroResult = Base10.decode(leadingZeroString).getOrThrow()
    leadingZeroResult shouldBeEqual "7B".decodeHex()

    val negativeString = "-123"
    val negativeResult = Base10.decode(negativeString).getOrThrow()
    negativeResult shouldBeEqual "7B".decodeHex()

    val positiveString = "+123"
    val positiveResult = Base10.decode(positiveString).getOrThrow()
    positiveResult shouldBeEqual "7B".decodeHex()
  }

  test("encode-decode round trip with hex input should return the original ByteString") {
    val originalHexString = "7B"
    val originalByteString = originalHexString.decodeHex()
    val encoded = Base10.encode(originalByteString).getOrThrow()
    val decoded = Base10.decode(encoded).getOrThrow()
    decoded shouldBeEqual originalByteString
  }

  test("encode-decode round trip for arbitrary integers") {
    // Define an arbitrary generator for ByteString based on integers
    val byteStringArb = Arb.int().map { int ->
      // Convert the generated int to a BigInteger and then to a ByteString
      BigInteger.fromInt(int).toByteArray().toByteString()
    }

    checkAll(byteStringArb) { originalByteString ->
      val encoded = Base10.encode(originalByteString).getOrThrow() // Encode the ByteString to a base-10 String
      val decoded = Base10.decode(encoded).getOrThrow() // Decode back to ByteString
      decoded shouldBeEqual originalByteString // Verify the round trip preserves the original ByteString
    }
  }

  test("encode-decode round trip for arbitrary ByteString using byteArray generator") {
    checkAll(
      Arb.byteArray(Arb.int(1..100), Arb.byte())
        .map { it.dropWhile { byte -> byte == 0.toByte() }.toByteArray() }
    ) { byteArray ->
      // Convert byteArray to ByteString, now with leading zeros removed
      val originalByteString = byteArray.toByteString()
      val encoded = Base10.encode(originalByteString).getOrThrow()
      val decoded = Base10.decode(encoded).getOrThrow()
      decoded shouldBeEqual originalByteString
    }
  }
})
