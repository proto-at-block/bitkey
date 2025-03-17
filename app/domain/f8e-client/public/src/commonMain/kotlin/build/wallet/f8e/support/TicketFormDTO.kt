package build.wallet.f8e.support

import build.wallet.ktor.result.RedactedResponseBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TicketFormDTO(
  val id: Long,
  @SerialName("ticket_fields")
  val fields: List<TicketFormFieldDTO>,
  val conditions: List<TicketFormConditionDTO>,
) : RedactedResponseBody
