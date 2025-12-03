package build.wallet.f8e.support

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
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
    ticket: CreateTicketDTO,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .unauthenticated()
      .bodyResult<CreateTicketResponse> {
        post("/api/customer_feedback") {
          withDescription("Create support ticket.")
          setRedactedBody(ticket)
          withEnvironment(f8eEnvironment)
        }
      }
      .logNetworkFailure { "Failed to create support ticket." }
      .mapUnit()
  }

  override suspend fun getFormStructure(
    f8eEnvironment: F8eEnvironment,
  ): Result<TicketFormDTO, NetworkingError> {
    return f8eHttpClient
      .unauthenticated()
      .bodyResult<TicketFormDTO> {
        get("/api/support/ticket-form") {
          withEnvironment(f8eEnvironment)
          withDescription("Fetch support ticket form.")
        }
      }
      .logNetworkFailure { "Failed to get ticket form structure" }
  }

  override suspend fun uploadAttachment(
    f8eEnvironment: F8eEnvironment,
    filename: String,
    mimeType: MimeType,
    source: Source,
  ): Result<String, NetworkingError> {
    return f8eHttpClient
      .unauthenticated()
      .bodyResult<AttachmentUploadResponse> {
        post("/api/support/attachments?filename=${filename.encodeURLQueryComponent()}") {
          withDescription("Upload attachment")
          headers["Content-Type"] = mimeType.name
          setUnredactedBody(StreamAssetContent(source))
          withEnvironment(f8eEnvironment)
        }
      }
      .logNetworkFailure { "Failed to upload attachment" }
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
