package build.wallet.f8e.partnerships

import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.Currency
import build.wallet.partnerships.PartnerInfo
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A struct representing the partner
 *
 * @property cryptoAmount represents the amount of bitcoin resulting from purchase
 * @property cryptoPrice represents the price of bitcoin
 * @property fiatCurrency represents the fiat currency that will be used for the purchase
 * @property networkFeeCrypto represents the network fee for the purchase transaction in bitcoin
 * @property networkFeeCrypto represents the network fee for the purchase transaction in fiat
 * @property partnerInfo represents the information about the partner offering the quote
 * @property userFeeFiat represents the fees for the transaction other than network fees
 */
@Serializable
data class Quote(
  @SerialName("crypto_amount")
  val cryptoAmount: Double,
  @SerialName("crypto_price")
  val cryptoPrice: Double,
  @SerialName("fiat_currency")
  val fiatCurrency: String,
  @SerialName("network_fee_crypto")
  val networkFeeCrypto: Double,
  @SerialName("network_fee_fiat")
  val networkFeeFiat: Double,
  @SerialName("partner_info")
  val partnerInfo: PartnerInfo,
  @SerialName("user_fee_fiat")
  val userFeeFiat: Double,
  @SerialName("quote_id")
  val quoteId: String?,
)

fun Quote.bitcoinAmount(): BitcoinMoney {
  return BitcoinMoney(BTC.fractionalUnitValueFromUnitValue(cryptoAmount.toBigDecimal()))
}

fun Quote.fiatCurrency(): Currency? {
  return when (fiatCurrency) {
    "USD" -> build.wallet.money.currency.USD
    "EUR" -> build.wallet.money.currency.EUR
    "GBP" -> build.wallet.money.currency.GBP
    else -> null
  }
}
