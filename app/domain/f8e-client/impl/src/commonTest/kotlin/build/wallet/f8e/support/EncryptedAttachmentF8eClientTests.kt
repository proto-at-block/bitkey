package build.wallet.f8e.support

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncryptedAttachmentF8eClientTests : FunSpec({

  val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  test("Create Encrypted Attachment - Response Deserialization") {
    val response = """
      {
        "encrypted_attachment_id": "test-id",
        "public_key": "cHVibGljLWtleQ=="
      }
    """.trimIndent()

    val expectedPublicKey = "public-key".toByteArray()

    val result = json.decodeFromString<CreateEncryptedAttachmentResponseBody>(response)
    result.encryptedAttachmentId.shouldBe("test-id")
    result.publicKey.toByteArray().shouldBe(expectedPublicKey)
  }

  test("Upload Sealed Attachment - Request Serialization") {
    val sealedData = "c2VhbGVkLWRhdGE=" // base64 encoded "sealed-data"
    val request = UploadSealedAttachmentRequestBody(sealedData)
    val result = json.encodeToString(request)

    result.shouldEqualJson(
      """
      {
        "sealed_attachment": "c2VhbGVkLWRhdGE="
      }
    """
    )
  }
})
