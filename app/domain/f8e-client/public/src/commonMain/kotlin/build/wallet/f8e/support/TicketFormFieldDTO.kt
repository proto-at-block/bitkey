package build.wallet.f8e.support

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TicketFormFieldDTO(
  val id: Long,
  val type: Type,
  @SerialName("known_type")
  val knownType: KnownType? = null,
  val required: Boolean,
  val title: String,
  val options: List<CustomFieldOptionDTO>?,
) {
  @Serializable
  enum class Type {
    // / Default custom field type when type is not specified
    Text,

    // / For multi-line text
    TextArea,

    // / To capture a boolean value. Allowed values are true or false
    CheckBox,

    // / Example: 2021-04-16
    Date,

    // / Enables users to choose multiple options from a dropdown menu
    MultiSelect,

    /**
     * Single-select dropdown menu.
     * It contains one or more tag values belonging to the field's options.
     * Example: ( {"id": 21938362, "value": ["hd_3000", "hd_5555"]})
     */
    Picker,
  }

  @Serializable
  enum class KnownType {
    Subject,
    Description,
    Country,
    AppVersion,
    AppInstallationID,
    PhoneMakeAndModel,
    SystemNameAndVersion,
    HardwareSerialNumber,
    HardwareFirmwareVersion,
  }

  @Serializable
  sealed interface Value {
    @Serializable
    @SerialName("String")
    data class Text(val value: String) : Value

    @Serializable
    @SerialName("Bool")
    data class Bool(val value: Boolean) : Value

    @Serializable
    @SerialName("MultiChoice")
    data class MultiChoice(val values: Set<String>) : Value
  }

  @Serializable
  data class CustomFieldOptionDTO(
    val id: Long,
    val name: String,
    val value: String,
  )
}
