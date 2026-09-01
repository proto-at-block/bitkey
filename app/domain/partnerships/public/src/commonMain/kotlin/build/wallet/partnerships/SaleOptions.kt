package build.wallet.partnerships

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server-driven fiat-denominated sell limits for available partners.
 */
@Serializable
data class SaleOptions(
  val country: String,
  @SerialName("fiat_currency")
  val fiatCurrency: String,
  @SerialName("min_fiat_amount")
  val minFiatAmount: Double,
  @SerialName("max_fiat_amount")
  val maxFiatAmount: Double,
  @SerialName("partner_limits")
  val partnerLimits: List<SalePartnerLimits>,
)

@Serializable
data class SalePartnerLimits(
  @SerialName("partner")
  val partner: PartnerId,
  @SerialName("min_fiat_amount")
  val minFiatAmount: Double,
  @SerialName("max_fiat_amount")
  val maxFiatAmount: Double,
)
