package build.wallet.serialization

import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class Base32Tests : FunSpec({
  test("encode crockford") {
    val alphabet = Base32.Alphabet.Crockford

    Base32.encode(
      "hello world!".encodeUtf8()
    ) shouldBeEqual "D1JPRV3F41VPYWKCCGGG"
    Base32.encode(
      "hello world!".encodeUtf8(),
      alphabet
    ) shouldBeEqual "D1JPRV3F41VPYWKCCGGG"
    Base32.encode(
      "HELLO WORLD!".encodeUtf8(),
      alphabet
    ) shouldBeEqual "912MRK2F41BMYMJC8GGG"

    Base32.encode(
      "F83E0F83E0".decodeHex(),
      alphabet
    ) shouldBeEqual "Z0Z0Z0Z0"
    Base32.encode(
      "07C1F07C1F".decodeHex(),
      alphabet
    ) shouldBeEqual "0Z0Z0Z0Z"
  }

  test("encode rfc4648 padded") {
    val alphabet = Base32.Alphabet.RFC4648(true)

    Base32.encode(
      "hello world!".encodeUtf8(),
      alphabet
    ) shouldBeEqual "NBSWY3DPEB3W64TMMQQQ===="

    Base32.encode(
      "F83E7F83E7".decodeHex(),
      alphabet
    ) shouldBeEqual "7A7H7A7H"
    Base32.encode(
      "77C1F77C1F".decodeHex(),
      alphabet
    ) shouldBeEqual "O7A7O7A7"
    Base32.encode(
      "F83E7F83".decodeHex(),
      alphabet
    ) shouldBeEqual "7A7H7AY="
  }

  test("encode rfc4648 not padded") {
    val alphabet = Base32.Alphabet.RFC4648(false)

    Base32.encode(
      "hello world!".encodeUtf8(),
      alphabet
    ) shouldBeEqual "NBSWY3DPEB3W64TMMQQQ"

    Base32.encode(
      "F83E7F83E7".decodeHex(),
      alphabet
    ) shouldBeEqual "7A7H7A7H"
    Base32.encode(
      "77C1F77C1F".decodeHex(),
      alphabet
    ) shouldBeEqual "O7A7O7A7"
    Base32.encode(
      "F83E7F83".decodeHex(),
      alphabet
    ) shouldBeEqual "7A7H7AY"
  }

  test("decode crockford") {
    val alphabet = Base32.Alphabet.Crockford

    Base32.decode(
      "D1JPRV3F41VPYWKCCGGG"
    ).getOrThrow() shouldBeEqual "hello world!".encodeUtf8()
    Base32.decode(
      "D1JPRV3F41VPYWKCCGGG",
      alphabet
    ).getOrThrow() shouldBeEqual "hello world!".encodeUtf8()

    Base32.decode(
      "Z0Z0Z0Z0",
      alphabet
    ).getOrThrow() shouldBeEqual "F83E0F83E0".decodeHex()
    Base32.decode(
      "0Z0Z0Z0Z",
      alphabet
    ).getOrThrow() shouldBeEqual "07C1F07C1F".decodeHex()

    Base32.decode(
      "IiLlOo",
      alphabet
    ).getOrThrow() shouldBeEqual
      Base32.decode("111100", alphabet).getOrThrow()
  }

  test("decode rfc4648 padded") {
    val alphabet = Base32.Alphabet.RFC4648(true)

    Base32.decode(
      "NBSWY3DPEB3W64TMMQQQ====",
      alphabet
    ).getOrThrow() shouldBeEqual "hello world!".encodeUtf8()

    Base32.decode(
      "7A7H7A7H",
      alphabet
    ).getOrThrow() shouldBeEqual "F83E7F83E7".decodeHex()
    Base32.decode(
      "O7A7O7A7",
      alphabet
    ).getOrThrow() shouldBeEqual "77C1F77C1F".decodeHex()
    Base32.decode(
      "7A7H7AY=",
      alphabet
    ).getOrThrow() shouldBeEqual "F83E7F83".decodeHex()
    Base32.decode(
      "7A7H7AY",
      alphabet
    ).getOrThrow() shouldBeEqual "F83E7F83".decodeHex()

    Base32.decode(
      ",",
      alphabet
    ).shouldBeErrOfType<Base32.Base32Error>()
  }

  test("decode rfc4648 not padded") {
    val alphabet = Base32.Alphabet.RFC4648(false)

    Base32.decode(
      "NBSWY3DPEB3W64TMMQQQ",
      alphabet
    ).getOrThrow() shouldBeEqual "hello world!".encodeUtf8()

    Base32.decode(
      "7A7H7A7H",
      alphabet
    ).getOrThrow() shouldBeEqual "F83E7F83E7".decodeHex()
    Base32.decode(
      "O7A7O7A7",
      alphabet
    ).getOrThrow() shouldBeEqual "77C1F77C1F".decodeHex()
    Base32.decode(
      "7A7H7AY=",
      alphabet
    ).getOrThrow() shouldBeEqual "F83E7F83".decodeHex()
    Base32.decode(
      "7A7H7AY",
      alphabet
    ).getOrThrow() shouldBeEqual "F83E7F83".decodeHex()

    Base32.decode(
      ",",
      alphabet
    ).shouldBeErrOfType<Base32.Base32Error>()
  }

  test("padding") {
    val alphabet = Base32.Alphabet.RFC4648(true)
    val numPadding = listOf(0, 6, 4, 3, 1)

    for (i in 1..5) {
      val input = ByteArray(i) { it.toByte() }.toByteString()
      val encoded = Base32.encode(input, alphabet)

      encoded.length shouldBeEqual 8

      val paddingCount = numPadding[i % 5]
      encoded.takeLast(paddingCount).all { it == '=' } shouldBe true
      encoded.dropLast(paddingCount).none { it == '=' } shouldBe true
    }
  }

  test("invertible crockford") {
    val alphabet = Base32.Alphabet.Crockford

    checkAll(Arb.byteArray(Arb.int(1..100), Arb.byte())) { data ->
      val encoded = Base32.encode(data.toByteString(), alphabet)
      val decoded = Base32.decode(encoded, alphabet).getOrThrow()
      decoded shouldBeEqual data.toByteString()
    }
  }

  test("invertible rfc4648 padded") {
    val alphabet = Base32.Alphabet.RFC4648(true)

    checkAll(Arb.byteArray(Arb.int(1..100), Arb.byte())) { data ->
      val encoded = Base32.encode(data.toByteString(), alphabet)
      val decoded = Base32.decode(encoded, alphabet).getOrThrow()
      decoded shouldBeEqual data.toByteString()
    }
  }

  test("invertible rfc4648 not padded") {
    val alphabet = Base32.Alphabet.RFC4648(false)

    checkAll(Arb.byteArray(Arb.int(1..100), Arb.byte())) { data ->
      val encoded = Base32.encode(data.toByteString(), alphabet)
      val decoded = Base32.decode(encoded, alphabet).getOrThrow()
      decoded shouldBeEqual data.toByteString()
    }
  }

  test("lower case") {
    val alphabet = Base32.Alphabet.Crockford
    // Define the Crockford Base32 alphabet
    val crockfordBase32Alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    val crockfordStringGen = arbitrary { rs ->
      val length = rs.random.nextInt(1, 101) // Choose a length between 1 and 100
      (1..length).map {
        crockfordBase32Alphabet.random(rs.random) // Select random characters from the alphabet
      }.joinToString("")
    }

    checkAll(crockfordStringGen) { data ->
      // Decode the original and its lowercase version
      val originalDecoded = Base32.decode(data, alphabet).getOrThrow()
      val lowerCaseDecoded = Base32.decode(data.lowercase(), alphabet).getOrThrow()

      // Assert that both decoded results are equal
      originalDecoded shouldBeEqual lowerCaseDecoded
    }
  }
})
