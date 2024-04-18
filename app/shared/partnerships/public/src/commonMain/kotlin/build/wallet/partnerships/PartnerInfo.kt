package build.wallet.partnerships

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A struct representing the partner
 *
 * @property logoUrl represents the URL of the partner logo
 * @property name represents the display name of the partner
 * @property partner represents the partner identifier
 */
@Serializable
data class PartnerInfo(
  @SerialName("logo_url")
  val logoUrl: String?,
  @SerialName("name")
  val name: String,
  @SerialName("partner")
  val partner: String,
)
