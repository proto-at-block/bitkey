package build.wallet.f8e.support

import bitkey.serialization.base64.ByteStringAsBase64Serializer
import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import build.wallet.support.EncryptedAttachment
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString

@BitkeyInject(AppScope::class)
class EncryptedAttachmentF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : EncryptedAttachmentF8eClient {
  override suspend fun createEncryptedAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<EncryptedAttachment, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<CreateEncryptedAttachmentResponseBody> {
        post("/api/support/encrypted-attachments") {
          setRedactedBody(EmptyRequestBody)
          withDescription("Create encrypted attachment")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
        }
      }
      .logNetworkFailure { "Failed to create encrypted attachment" }
      .map {
        EncryptedAttachment(
          encryptedAttachmentId = it.encryptedAttachmentId,
          publicKey = it.publicKey
        )
      }
  }

  override suspend fun uploadSealedAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    encryptedAttachmentId: String,
    sealedAttachment: String,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<EmptyResponseBody> {
        put("/api/support/encrypted-attachments/$encryptedAttachmentId") {
          withDescription("Upload sealed attachment")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          setRedactedBody(
            UploadSealedAttachmentRequestBody(
              sealedAttachment = sealedAttachment
            )
          )
        }
      }
      .logNetworkFailure { "Failed to upload sealed attachment" }
      .mapUnit()
  }
}

@Serializable
data class CreateEncryptedAttachmentResponseBody(
  @SerialName("encrypted_attachment_id")
  val encryptedAttachmentId: String,
  @SerialName("public_key")
  @Serializable(with = ByteStringAsBase64Serializer::class)
  val publicKey: ByteString,
) : RedactedResponseBody

@Serializable
data class UploadSealedAttachmentRequestBody(
  @SerialName("sealed_attachment")
  val sealedAttachment: String,
) : RedactedRequestBody
