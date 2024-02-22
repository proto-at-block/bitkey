package build.wallet.f8e.support

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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

class SupportTicketServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : SupportTicketService {
  override suspend fun createTicket(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    ticket: CreateTicketDTO,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId
      )
      .bodyResult<CreateTicketResponse> {
        post("/api/customer_feedback") {
          setBody(
            ticket
          )
        }
      }
      .logNetworkFailure { "Couldn't create support ticket." }
      .mapUnit()
  }

  override suspend fun getFormStructure(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
  ): Result<TicketFormDTO, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId
      )
      .bodyResult<TicketFormDTO> {
        get("/api/support/ticket-form")
      }
      .logNetworkFailure { "Couldn't fetch support ticket form." }
  }

  override suspend fun uploadAttachment(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    filename: String,
    mimeType: MimeType,
    source: Source,
  ): Result<String, NetworkingError> {
    return f8eHttpClient
      .authenticated(
        f8eEnvironment = f8eEnvironment,
        accountId = accountId
      )
      .bodyResult<AttachmentUploadResponse> {
        post("/api/support/attachments?filename=${filename.encodeURLQueryComponent()}") {
          headers["Content-Type"] = mimeType.name
          setBody(StreamAssetContent(source))
        }
      }
      .logNetworkFailure { "Couldn't upload attachment." }
      .map { it.token }
  }

  @Serializable
  private data class CreateTicketResponse(
    @SerialName("request_id")
    val requestId: Long,
  )

  @Serializable
  private data class AttachmentUploadResponse(
    val token: String,
  )
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
