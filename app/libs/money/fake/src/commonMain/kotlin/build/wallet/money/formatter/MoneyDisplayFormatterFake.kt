package build.wallet.money.formatter

import build.wallet.amount.DoubleFormatterImpl
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.platform.settings.LocaleProviderFake

/**
 * Many UI state machine tests depend on real implementation of [MoneyDisplayFormatter].
 * To prevent leaking dependency on `:money:impl` modules everywhere,
 * use this semi-fake implementation in tests.
 */
val MoneyDisplayFormatterFake: MoneyDisplayFormatter = MoneyDisplayFormatterImpl(
  bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake(),
  moneyFormatterDefinitions = MoneyFormatterDefinitionsImpl(
    doubleFormatter = DoubleFormatterImpl(
      localeProvider = LocaleProviderFake()
    )
  ),
  bip177FeatureFlag = Bip177FeatureFlag(
    featureFlagDao = FeatureFlagDaoFake()
  )
)
