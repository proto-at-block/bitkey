package build.wallet.f8e.support

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TicketFormDTO(
  val id: Long,
  @SerialName("ticket_fields")
  val fields: List<TicketFormFieldDTO>,
  val conditions: List<TicketFormConditionDTO>,
)
