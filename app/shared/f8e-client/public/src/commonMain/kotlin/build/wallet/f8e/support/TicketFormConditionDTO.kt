package build.wallet.f8e.support

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TicketFormConditionDTO(
  @SerialName("parent_field_id")
  val parentFieldId: Long,
  val value: TicketFormFieldDTO.Value,
  @SerialName("child_fields")
  val childFields: List<ChildFieldVisibilityDTO>,
) {
  @Serializable
  data class ChildFieldVisibilityDTO(
    val id: Long,
    @SerialName("required")
    val isRequired: Boolean,
  )
}
