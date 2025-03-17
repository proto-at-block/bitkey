package build.wallet.money.input

/**
 * Represents what to display to the customer as they are inputting a monetary amount.
 */
data class MoneyInputDisplayText(
  /**
   * The overall display text to show for the monetary amount.
   */
  val displayText: String,
  /**
   * An optional substring of [displayText] to display with a "ghosted" style in order
   * to demonstrate placeholder fractional digits that haven't been entered yet.
   */
  val displayTextGhostedSubstring: Substring? = null,
) {
  data class Substring(
    val string: String,
    val range: IntRange,
  )
}
