package bitkey.serialization.base64

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString

object ByteStringAsBase64SerializerTests : FunSpec({

  val bytes = ByteString.of(1, 2, 3) // [0x01,0x02,0x03]
  val sampleValue = SampleValue(bytes)
  val sampleValueJson = """{"bytes":"AQID"}""" // "AQID" is Base64 of 0x01 0x02 0x03

  test("serialize") {
    Json.encodeToString(sampleValue).shouldBe(sampleValueJson)
  }

  test("deserialize") {
    Json.decodeFromString<SampleValue>(sampleValueJson).shouldBe(sampleValue)
  }

  test("round-trip equality") {
    // encode then decode yields the same object
    val json = Json.encodeToString(sampleValue)
    Json.decodeFromString<SampleValue>(json).shouldBe(sampleValue)
  }

  test("empty ByteString") {
    val empty = SampleValue(ByteString.EMPTY)
    val emptyJson = """{"bytes":""}"""
    Json.encodeToString(empty).shouldBe(emptyJson)
    Json.decodeFromString<SampleValue>(emptyJson).shouldBe(empty)
  }

  test("single-byte padding") {
    val one = SampleValue(ByteString.of(0xFF.toByte()))
    // 0xFF → "/w==" in Base64
    Json.encodeToString(one).shouldBe("""{"bytes":"/w=="}""")
    Json.decodeFromString<SampleValue>("""{"bytes":"/w=="}""").shouldBe(one)
  }

  test("invalid Base64 input throws") {
    val badJson = """{"bytes":"!!!not_base64$$$"}"""
    shouldThrow<IllegalArgumentException> {
      Json.decodeFromString<SampleValue>(badJson)
    }
  }
})

@Serializable
private data class SampleValue(
  @Serializable(with = ByteStringAsBase64Serializer::class)
  val bytes: ByteString,
)
