package build.wallet.money.display

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Display unit for Bitcoin amounts
 *
 * Use [displayText] and [appearanceLabel] to get the appropriate display text.
 */
@Serializable
enum class BitcoinDisplayUnit {
  /** Display the amount in the fractional unit as a whole number, i.e. ₿100,000 or 100,000 sats */
  @SerialName("SATOSHI")
  Satoshi,

  /** Display the amount in the main unit as a decimal value, i.e. 0.001 BTC */
  @SerialName("BITCOIN")
  Bitcoin,
}

/**
 * Returns the display text for the unit selection sheet.
 * Satoshi shows "₿ (formerly sats)" per BIP 177.
 *
 * TODO: W-15176 remove "(formerly sats)" once BIP 177 rollout is complete and we stop surfacing the legacy name.
 */
fun BitcoinDisplayUnit.displayText(): String =
  when (this) {
    BitcoinDisplayUnit.Satoshi -> "₿ (formerly sats)"
    BitcoinDisplayUnit.Bitcoin -> "BTC"
  }

/**
 * Returns the label shown in the appearance preference screen.
 * Satoshi shows "₿" per BIP 177.
 */
fun BitcoinDisplayUnit.appearanceLabel(): String =
  when (this) {
    BitcoinDisplayUnit.Satoshi -> "₿"
    BitcoinDisplayUnit.Bitcoin -> "BTC"
  }
