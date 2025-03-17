package build.wallet.partnerships

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents various partner info
 *
 * @property logoUrl represents the URL of the partner logo
 * @property logoBadgedUrl represents the URL of the partner logo with a badge
 * @property name represents the display name of the partner
 * @property partnerId represents the partner identifier
 */
@Serializable
data class PartnerInfo(
  @SerialName("logo_url")
  val logoUrl: String?,
  @SerialName("logo_badged_url")
  val logoBadgedUrl: String?,
  @SerialName("name")
  val name: String,
  @SerialName("partner")
  val partnerId: PartnerId,
)
