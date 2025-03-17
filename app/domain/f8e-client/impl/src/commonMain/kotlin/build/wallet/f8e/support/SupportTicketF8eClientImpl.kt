package build.wallet.f8e.support

import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.ktor.result.setUnredactedBody
import build.wallet.mapUnit
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.content.OutgoingContent
import io.ktor.http.encodeURLQueryComponent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.ByteReadPacket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.Buffer
import okio.Source
import okio.use

@BitkeyInject(AppScope::class)
class SupportTicketF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : SupportTicketF8eClient {
  override suspend fun createTicket(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    ticket: CreateTicketDTO,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<CreateTicketResponse> {
        post("/api/customer_feedback") {
          withDescription("Create support ticket.")
          setRedactedBody(ticket)
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
        }
      }
      .mapUnit()
  }

  override suspend fun getFormStructure(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<TicketFormDTO, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<TicketFormDTO> {
        get("/api/support/ticket-form") {
          withEnvironment(f8eEnvironment)
          withAccountId(accountId, AuthTokenScope.Recovery)
          withDescription("Fetch support ticket form.")
        }
      }
  }

  override suspend fun uploadAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    filename: String,
    mimeType: MimeType,
    source: Source,
  ): Result<String, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<AttachmentUploadResponse> {
        post("/api/support/attachments?filename=${filename.encodeURLQueryComponent()}") {
          withDescription("Upload attachment")
          headers["Content-Type"] = mimeType.name
          setUnredactedBody(StreamAssetContent(source))
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
        }
      }
      .map { it.token }
  }

  @Serializable
  private data class CreateTicketResponse(
    @SerialName("request_id")
    val requestId: Long,
  ) : RedactedResponseBody

  @Serializable
  private data class AttachmentUploadResponse(
    val token: String,
  ) : RedactedResponseBody
}

// / Used to upload from an Okio `Source`
private class StreamAssetContent(
  private val fileContentStream: Source,
) : OutgoingContent.WriteChannelContent() {
  override suspend fun writeTo(channel: ByteWriteChannel) {
    val contentBuffer = Buffer()
    fileContentStream.use {
      while (it.read(contentBuffer, BUFFER_SIZE) != -1L) {
        contentBuffer.readByteArray().let { content ->
          channel.writePacket(ByteReadPacket(content))
        }
      }
    }
    channel.flush()
    channel.close()
  }

  private companion object {
    const val BUFFER_SIZE = 1024 * 8L
  }
}
