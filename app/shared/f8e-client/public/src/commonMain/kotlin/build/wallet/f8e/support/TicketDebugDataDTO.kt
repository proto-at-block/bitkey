package build.wallet.f8e.support

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TicketDebugDataDTO(
  @SerialName("app_version")
  val appVersion: String,
  @SerialName("app_installation_id")
  val appInstallationId: String,
  @SerialName("phone_make_and_model")
  val phoneMakeAndModel: String,
  @SerialName("system_name_and_version")
  val systemNameAndVersion: String,
  @SerialName("hardware_firmware_version")
  val hardwareFirmwareVersion: String,
  @SerialName("hardware_serial_number")
  val hardwareSerialNumber: String,
  @SerialName("feature_flags")
  val featureFlags: Map<String, String>,
)
