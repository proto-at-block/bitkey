package build.wallet.encrypt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class XSealedDataFormatSerializerTests : FunSpec({
  val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
  }

  test("serializes Standard to 1") {
    json.encodeToString(XSealedDataFormatSerializer, XSealedData.Format.Standard) shouldBe "1"
  }

  test("serializes WithPubkey to 2") {
    json.encodeToString(XSealedDataFormatSerializer, XSealedData.Format.WithPubkey) shouldBe "2"
  }

  test("deserializes 1 to Standard") {
    json.decodeFromString(XSealedDataFormatSerializer, "1") shouldBe XSealedData.Format.Standard
  }

  test("deserializes 2 to WithPubkey") {
    json.decodeFromString(XSealedDataFormatSerializer, "2") shouldBe XSealedData.Format.WithPubkey
  }

  test("throws on invalid format code") {
    val exception = runCatching {
      json.decodeFromString(XSealedDataFormatSerializer, "99")
    }.exceptionOrNull()
    exception shouldBe IllegalArgumentException("Invalid format number: 99")
  }
})
