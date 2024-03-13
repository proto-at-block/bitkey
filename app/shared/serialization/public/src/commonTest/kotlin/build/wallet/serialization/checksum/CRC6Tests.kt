package build.wallet.serialization.checksum

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

class CRC6Tests : FunSpec({
  data class TestCase(val data: ByteString, val crc: Byte)

  context("test crc6") {
    withData(
      // Test case defined in https://infineon.github.io/mtb-pdl-cat2/pdl_api_reference_manual/html/group__group__crypto__lld__crc__functions.html
      TestCase("123456789".encodeUtf8(), 0x0D),
      // Single char
      TestCase("A".encodeUtf8(), 0x2F),
      // Empty String
      TestCase("".encodeUtf8(), 0x3F),
      TestCase("hello world 123".encodeUtf8(), 0x19),
      TestCase("deadbeef".decodeHex(), 0x2A)
    ) { (data, crc) ->
      withClue("data: $data, crc: $crc") {
        CRC6.calculate(data.toByteArray()) shouldBe crc
      }
    }
  }
})
