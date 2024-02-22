package build.wallet.f8e.partnerships

import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A data class containing payment methods and their options
 *
 * @property country the country code for the purchase options
 * @property fiatCurrency the fiat currency code for the purchase options
 * @property paymentMethods the map of payment methods to their options
 */
@Serializable
data class PurchaseOptions(
  @SerialName("country")
  val country: String,
  @SerialName("fiat_currency")
  val fiatCurrency: String,
  @SerialName("payment_methods")
  val paymentMethods: Map<String, PaymentMethodOptions>,
)

/**
 * A struct representing the options for a payment method
 * All amounts are in a fiat currency contained in the parent [PurchaseOptions]
 * The amounts correspond to the unit value of the fiat currency (e.g. 15.5 = $15.50).
 *
 * @property defaultPurchaseAmount the default selected purchase amount
 * @property displayPurchaseAmounts the list of purchase amounts to display
 * @property minPurchaseAmount the minimum purchase amount for custom selection
 * @property maxPurchaseAmount the maximum purchase amount for custom selection
 */
@Serializable
data class PaymentMethodOptions(
  @SerialName("default_purchase_amount")
  val defaultPurchaseAmount: Double,
  @SerialName("display_purchase_amounts")
  val displayPurchaseAmounts: List<Double>,
  @SerialName("min_purchase_amount")
  val minPurchaseAmount: Double,
  @SerialName("max_purchase_amount")
  val maxPurchaseAmount: Double,
)

fun PurchaseOptions.toPurchaseMethodAmounts(
  currency: FiatCurrency,
  paymentMethod: String,
): Result<PurchaseMethodAmounts, Error> {
  val cardPaymentOptions =
    paymentMethods[paymentMethod]
      ?.let {
        if (it.displayPurchaseAmounts.isEmpty()) {
          return Err(Error("No display amounts available for payment method: $paymentMethod"))
        }
        it
      } ?: return Err(Error("No purchase options available for payment method: $paymentMethod"))

  val displayOptions =
    cardPaymentOptions.displayPurchaseAmounts.map { FiatMoney(currency, it.toBigDecimal()) }

  val cardAmounts =
    PurchaseMethodAmounts(
      default = FiatMoney(currency, cardPaymentOptions.defaultPurchaseAmount.toBigDecimal()),
      displayOptions = displayOptions,
      min = FiatMoney(currency, cardPaymentOptions.minPurchaseAmount.toBigDecimal()),
      max = FiatMoney(currency, cardPaymentOptions.maxPurchaseAmount.toBigDecimal())
    )
  return Ok(cardAmounts)
}

data class PurchaseMethodAmounts(
  val default: FiatMoney,
  val displayOptions: List<FiatMoney>,
  val min: FiatMoney,
  val max: FiatMoney,
)
