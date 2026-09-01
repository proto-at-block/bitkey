package bitkey.serialization.base32

import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class Base32EncodingTests : FunSpec({
  test("encode with canonical Crockford vectors") {
    Base32Encoding.encode("hello world!".encodeUtf8()).getOrThrow()
      .shouldBeEqual("D1JPRV3F41VPYWKCCGGG")
    Base32Encoding.encode("F83E0F83E0".decodeHex()).getOrThrow()
      .shouldBeEqual("Z0Z0Z0Z0")
    Base32Encoding.encode("07C1F07C1F".decodeHex()).getOrThrow()
      .shouldBeEqual("0Z0Z0Z0Z")
  }

  test("round trip byte arrays of all valid output lengths") {
    checkAll(Arb.byteArray(Arb.int(0..256), Arb.byte())) { data ->
      val encoded = Base32Encoding.encode(data.toByteString()).getOrThrow()
      Base32Encoding.decode(encoded).getOrThrow().shouldBeEqual(data.toByteString())
    }
  }

  test("decode canonical Crockford vectors") {
    Base32Encoding.decode("D1JPRV3F41VPYWKCCGGG").getOrThrow()
      .shouldBeEqual("hello world!".encodeUtf8())
    Base32Encoding.decode("Z0Z0Z0Z0").getOrThrow()
      .shouldBeEqual("F83E0F83E0".decodeHex())
    Base32Encoding.decode("0Z0Z0Z0Z").getOrThrow()
      .shouldBeEqual("07C1F07C1F".decodeHex())
  }

  test("decode is case insensitive and handles ambiguous Crockford characters") {
    Base32Encoding.decode("d1jprv3f41vpywkccggg").getOrThrow()
      .shouldBeEqual("hello world!".encodeUtf8())
    Base32Encoding.decode("IiLlOo00").getOrThrow()
      .shouldBeEqual(Base32Encoding.decode("11110000").getOrThrow())
  }

  test("decode ignores hyphens and whitespace") {
    Base32Encoding.decode("D1JP-RV3F 41VP\nYWKC\tCGGG").getOrThrow()
      .shouldBeEqual("hello world!".encodeUtf8())
  }

  test("decode rejects invalid characters and truncated input") {
    Base32Encoding.decode("*").shouldBeErrOfType<Base32.Base32Error>()
    Base32Encoding.decode("0").shouldBeErrOfType<Base32.Base32Error>()
    Base32Encoding.decode("000").shouldBeErrOfType<Base32.Base32Error>()
    Base32Encoding.decode("000000").shouldBeErrOfType<Base32.Base32Error>()
    Base32Encoding.decode("00=").shouldBeErrOfType<Base32.Base32Error>()
  }
})
