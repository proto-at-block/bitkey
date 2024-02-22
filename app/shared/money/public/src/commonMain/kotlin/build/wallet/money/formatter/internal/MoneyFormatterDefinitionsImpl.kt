package build.wallet.money.formatter.internal

import build.wallet.amount.DoubleFormatter
import build.wallet.money.formatter.internal.FiatMoneyFormatter.CurrencyRepresentationOption.SYMBOL
import build.wallet.money.formatter.internal.FiatMoneyFormatter.SignOption.SIGNED
import build.wallet.money.formatter.internal.FiatMoneyFormatter.SignOption.STANDARD

class MoneyFormatterDefinitionsImpl(
  doubleFormatter: DoubleFormatter,
) : MoneyFormatterDefinitions {
  override val fiatStandard =
    FiatMoneyFormatter(
      omitsFractionalUnitIfPossible = false,
      currencyRepresentationOption = SYMBOL,
      signOption = STANDARD,
      doubleFormatter = doubleFormatter
    )

  override val fiatStandardWithSign =
    FiatMoneyFormatter(
      omitsFractionalUnitIfPossible = false,
      currencyRepresentationOption = SYMBOL,
      signOption = SIGNED,
      doubleFormatter = doubleFormatter
    )

  override val fiatCompact =
    FiatMoneyFormatter(
      omitsFractionalUnitIfPossible = true,
      currencyRepresentationOption = SYMBOL,
      signOption = STANDARD,
      doubleFormatter = doubleFormatter
    )

  override val bitcoinCode =
    BitcoinMoneyFormatter(
      denominationOption =
        BitcoinMoneyFormatter.DenominationOption.Bitcoin(
          shouldOmitTrailingZeros = false
        ),
      doubleFormatter = doubleFormatter
    )

  override val bitcoinReducedCode =
    BitcoinMoneyFormatter(
      denominationOption =
        BitcoinMoneyFormatter.DenominationOption.Bitcoin(
          shouldOmitTrailingZeros = true
        ),
      doubleFormatter = doubleFormatter
    )

  override val bitcoinFractionalNameOnly =
    BitcoinMoneyFormatter(
      denominationOption = BitcoinMoneyFormatter.DenominationOption.Satoshi,
      doubleFormatter = doubleFormatter
    )
}
