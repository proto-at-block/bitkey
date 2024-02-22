package build.wallet.money.display

/**
 * Display unit for Bitcoin amounts
 *
 * @property displayText: How the unit is displayed as an option when choosing it
 */
enum class BitcoinDisplayUnit(val displayText: String) {
  /** Display the amount in the main unit as a decimal value, i.e. 0.001 BTC */
  Bitcoin(displayText = "Bitcoin"),

  /** Display the amount in the fractional unit as a whole number, i.e. 100,000 sats  */
  Satoshi(displayText = "Sats"),
}
