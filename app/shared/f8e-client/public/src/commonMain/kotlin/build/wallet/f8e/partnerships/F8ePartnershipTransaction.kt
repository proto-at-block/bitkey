package build.wallet.f8e.partnerships

import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.partnerships.PartnershipTransactionStatus
import build.wallet.partnerships.PartnershipTransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * F8e representation of a [PartnershipTransaction].
 *
 * This is the status of a transaction as returned by the F8e api,
 * and does not include local data stored like created/update times.
 * Additionally, unlike the local model, this will always have a
 * known status.
 */
@Serializable
data class F8ePartnershipTransaction(
  val id: PartnershipTransactionId,
  val type: PartnershipTransactionType,
  val status: PartnershipTransactionStatus,
  val context: String?,
  @SerialName("partner_info")
  val partnerInfo: PartnerInfo,
  @SerialName("crypto_amount")
  val cryptoAmount: Double?,
  val txid: String?,
  @SerialName("fiat_amount")
  val fiatAmount: Double?,
  @SerialName("fiat_currency")
  val fiatCurrency: IsoCurrencyTextCode?,
  @SerialName("payment_method")
  val paymentMethod: String?,
)
