@file:OptIn(ExperimentalSerializationApi::class)

package build.wallet.serialization.json

import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class JsonEncodingTests : FunSpec({
  val sampleValue =
    Sample(
      foo = 42,
      bar = "hello world!"
    )
  val sampleJson = """{"foo":42,"bar":"hello world!"}"""
  val sampleJsonMissingField = """{"foo":42}"""
  val sampleJsonFieldOfWrongType = """{"foo":"test","bar":"hello world!"}"""

  context("decode") {
    test("success") {
      Json.decodeFromStringResult<Sample>(sampleJson).shouldBe(Ok(sampleValue))
    }

    test("failure - missing field") {
      Json.decodeFromStringResult<Sample>(sampleJsonMissingField)
        .shouldBeErrOfType<JsonDecodingError>()
    }

    test("failure - field is of a wrong type") {
      Json.decodeFromStringResult<Sample>(sampleJsonFieldOfWrongType)
        .shouldBeErrOfType<JsonDecodingError>()
    }

    test("failure - empty JSON object") {
      Json.decodeFromStringResult<Sample>(json = "{}")
        .shouldBeErrOfType<JsonDecodingError>()
    }
  }

  context("encode") {
    test("success") {
      Json.encodeToStringResult(sampleValue).shouldBe(Ok(sampleJson))
    }
  }
})

@Serializable
private data class Sample(
  val foo: Int,
  val bar: String,
)
