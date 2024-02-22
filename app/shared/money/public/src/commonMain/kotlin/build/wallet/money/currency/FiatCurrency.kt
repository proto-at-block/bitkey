package build.wallet.money.currency

import build.wallet.money.currency.code.IsoCurrencyTextCode
import de.cketti.codepoints.deluxe.appendCodePoint
import de.cketti.codepoints.deluxe.codePointSequence
import de.cketti.codepoints.deluxe.toCodePoint

data class FiatCurrency(
  override val textCode: IsoCurrencyTextCode,
  override val unitSymbol: String?,
  override val fractionalDigits: Int,
  /** Display configurations for fiat currencies. */
  val displayConfiguration: DisplayConfiguration,
) : Currency {
  data class DisplayConfiguration(
    /** The display name of the currency */
    val name: String,
    /** The country code for the currency to use to display a flag */
    val displayCountryCode: String,
  ) {
    val flagEmoji = getFlagEmoji(displayCountryCode)

    /**
     * Returns the unicode string for a flag emoji for the given 2 letter code
     * Note: the 2-letter code is usually an alpha-2 country code, but could also be 'EU'.
     */
    private fun getFlagEmoji(alpha2Code: String): String {
      // Offset between uppercase ASCII and regional indicator symbols
      val asciiToIndicatorOffset = 127397
      return StringBuilder().apply {
        alpha2Code.codePointSequence().forEach {
          // We get the ascii value of the [alpha2Code] and convert it to the regional indicator value
          // which combined will reference the flag emoji. See https://blog.emojipedia.org/emoji-flags-explained/
          appendCodePoint((it.value + asciiToIndicatorOffset).toCodePoint())
        }
      }.toString()
    }
  }
}
