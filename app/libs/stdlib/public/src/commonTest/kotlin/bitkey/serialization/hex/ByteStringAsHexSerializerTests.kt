package bitkey.serialization.hex

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString

class ByteStringAsHexSerializerTests : FunSpec({
  val bytes = ByteString.of(1, 2, 3)
  val sampleValue = SampleValue(bytes)
  val sampleValueJson = """{"bytes":"010203"}"""

  test("serialize") {
    Json.encodeToString(sampleValue).shouldBe(sampleValueJson)
  }

  test("deserialize") {
    Json.decodeFromString<SampleValue>(sampleValueJson).shouldBe(sampleValue)
  }
})

@Serializable
private data class SampleValue(
  @Serializable(with = ByteStringAsHexSerializer::class)
  val bytes: ByteString,
)
