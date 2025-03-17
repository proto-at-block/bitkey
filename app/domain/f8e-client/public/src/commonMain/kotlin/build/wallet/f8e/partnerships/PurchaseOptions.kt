package build.wallet.f8e.partnerships

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
