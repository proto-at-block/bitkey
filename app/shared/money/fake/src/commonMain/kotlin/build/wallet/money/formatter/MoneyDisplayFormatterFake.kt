package build.wallet.money.formatter

import build.wallet.amount.DoubleFormatterImpl
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.platform.settings.LocaleProviderFake

// Uses the Impl MoneyDisplayFormatterImpl so we can test real expected display strings in other tests.
val MoneyDisplayFormatterFake: MoneyDisplayFormatter =
  MoneyDisplayFormatterImpl(
    bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock(),
    moneyFormatterDefinitions =
      MoneyFormatterDefinitionsImpl(
        doubleFormatter =
          DoubleFormatterImpl(
            localeProvider = LocaleProviderFake()
          )
      )
  )
