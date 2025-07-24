package build.wallet.money.display

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Display unit for Bitcoin amounts
 *
 * @property displayText: How the unit is displayed as an option when choosing it
 */
@Serializable
enum class BitcoinDisplayUnit(val displayText: String) {
  /** Display the amount in the main unit as a decimal value, i.e. 0.001 BTC */
  @SerialName("BITCOIN")
  Bitcoin(displayText = "BTC"),

  /** Display the amount in the fractional unit as a whole number, i.e. 100,000 sats  */
  @SerialName("SATOSHI")
  Satoshi(displayText = "Sats"),
}
