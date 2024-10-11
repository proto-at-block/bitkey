package build.wallet.partnerships

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A struct representing a single bitcoin sale quote.
 */
@Serializable
data class SaleQuote(
  @SerialName("crypto_amount")
  val cryptoAmount: Double,
  @SerialName("fiat_amount")
  val fiatAmount: Double,
  @SerialName("fiat_currency")
  val fiatCurrency: String,
  @SerialName("partner_info")
  val partnerInfo: PartnerInfo,
  @SerialName("user_fee_fiat")
  val userFeeFiat: Double,
)
