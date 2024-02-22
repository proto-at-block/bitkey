package build.wallet.money.formatter

import build.wallet.amount.DoubleFormatterImpl
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.formatter.internal.MoneyDisplayFormatterImpl
import build.wallet.money.formatter.internal.MoneyFormatterDefinitionsImpl
import build.wallet.platform.settings.LocaleIdentifierProviderFake

// Use the Impl MoneyDiplayFormatter so we can test real expected display strings in other tests.
val MoneyDisplayFormatterFake =
  MoneyDisplayFormatterImpl(
    bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock(),
    moneyFormatterDefinitions =
      MoneyFormatterDefinitionsImpl(
        doubleFormatter =
          DoubleFormatterImpl(
            localeIdentifierProvider = LocaleIdentifierProviderFake()
          )
      )
  )
