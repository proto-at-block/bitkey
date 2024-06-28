package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.statemachine.data.account.OnboardConfig
import build.wallet.statemachine.data.keybox.AccountData.HasActiveLiteAccountData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData

val HasActiveLiteAccountDataFake =
  HasActiveLiteAccountData(
    account = LiteAccountMock,
    onboardConfig = OnboardConfig(stepsToSkip = emptySet()),
    accountUpgradeTemplateFullAccountConfigData =
      LoadedTemplateFullAccountConfigData(
        config = FullAccountConfigMock,
        updateConfig = {}
      ),
    onUpgradeAccount = {}
  )
