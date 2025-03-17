package build.wallet.f8e.support

import build.wallet.ktor.result.RedactedRequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateTicketDTO(
  val email: String,
  @SerialName("form_id")
  val formId: Long,
  val subject: String,
  val description: String,
  @SerialName("custom_field_values")
  val customFieldValues: Map<Long, TicketFormFieldDTO.Value>,
  val attachments: List<AttachmentUploadResultDTO>,
  @SerialName("debug_data")
  val debugData: TicketDebugDataDTO?,
) : RedactedRequestBody {
  @Serializable
  sealed interface AttachmentUploadResultDTO {
    @Serializable
    @SerialName("Success")
    data class Success(
      val token: String,
    ) : AttachmentUploadResultDTO

    @Serializable
    @SerialName("Failure")
    data class Failure(
      val filename: String,
      @SerialName("mime_type")
      val mimeType: String,
      val error: String,
    ) : AttachmentUploadResultDTO
  }
}
