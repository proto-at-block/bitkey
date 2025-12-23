package build.wallet.money.display

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Display unit for Bitcoin amounts
 *
 * Use [displayText] and [appearanceLabel] with [isBip177Enabled] to get the appropriate
 * text based on whether the BIP 177 feature flag is enabled.
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
 * When BIP 177 is enabled, Satoshi shows "₿ (formerly sats)", otherwise "sats".
 *
 * TODO: W-15176 remove "(formerly sats)" once BIP 177 rollout is complete and we stop surfacing the legacy name.
 */
fun BitcoinDisplayUnit.displayText(isBip177Enabled: Boolean): String =
  when (this) {
    BitcoinDisplayUnit.Satoshi -> if (isBip177Enabled) "₿ (formerly sats)" else "sats"
    BitcoinDisplayUnit.Bitcoin -> "BTC"
  }

/**
 * Returns the label shown in the appearance preference screen.
 * When BIP 177 is enabled, Satoshi shows "₿", otherwise "sats".
 */
fun BitcoinDisplayUnit.appearanceLabel(isBip177Enabled: Boolean): String =
  when (this) {
    BitcoinDisplayUnit.Satoshi -> if (isBip177Enabled) "₿" else "sats"
    BitcoinDisplayUnit.Bitcoin -> "BTC"
  }
