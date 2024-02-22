package build.wallet.money.formatter.internal

interface MoneyFormatterDefinitions {
  /**
   * Typical display of dollars and cents, e.g. "$25.00" or "¥1,000"
   */
  val fiatStandard: MoneyFormatter

  /**
   * Typical display of dollars and cents with signs, e.g. "+ $25.00" or "- $1.50"
   */
  val fiatStandardWithSign: MoneyFormatter

  /**
   * Typical display of dollars only, or dollars and cents, e.g. "$25" or "$24.99"
   */
  val fiatCompact: FiatMoneyFormatter

  /**
   * Typical display of reduced dollars and cents with currency code, e.g., "1.5 BTC".
   * Used only for rendering BTC.
   */
  val bitcoinReducedCode: BitcoinMoneyFormatter

  /**
   * Typical display of dollars and cents with currency code, e.g., "1.50000000 BTC".
   * Used only for rendering BTC.
   */
  val bitcoinCode: BitcoinMoneyFormatter

  /**
   * Used only for rendering BTC as satoshis, e.g. "1 sats", or "100,000,000 sats"
   */
  val bitcoinFractionalNameOnly: BitcoinMoneyFormatter
}
