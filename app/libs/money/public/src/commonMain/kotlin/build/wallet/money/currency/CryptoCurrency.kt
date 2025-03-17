package build.wallet.money.currency

import build.wallet.money.currency.code.IsoCurrencyTextCode

data class CryptoCurrency internal constructor(
  override val textCode: IsoCurrencyTextCode,
  override val unitSymbol: String,
  /** Configuration for fractional units, i.e. satoshis. */
  val fractionalUnitConfiguration: FractionalUnitConfiguration,
) : Currency {
  data class FractionalUnitConfiguration(
    /** e.g. "sat" */
    val name: String,
    /** e.g. "sats" */
    val namePlural: String?,
    /**
     *  The number of digits of this fractional unit per the currency's main unit.
     *  e.g. digits: 8   =>   100,000,000 fractional units per unit (like for BTC)
     */
    val digits: Int,
  )

  override val fractionalDigits: Int = fractionalUnitConfiguration.digits
}
