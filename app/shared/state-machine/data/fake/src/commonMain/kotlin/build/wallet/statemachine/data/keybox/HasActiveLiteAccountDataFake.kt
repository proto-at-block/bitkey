package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.statemachine.data.account.create.LoadedOnboardConfigDataMock
import build.wallet.statemachine.data.keybox.AccountData.HasActiveLiteAccountData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData

val HasActiveLiteAccountDataFake =
  HasActiveLiteAccountData(
    account = LiteAccountMock,
    accountUpgradeOnboardConfigData = LoadedOnboardConfigDataMock,
    accountUpgradeTemplateKeyboxConfigData =
      LoadedTemplateKeyboxConfigData(
        config = KeyboxConfigMock,
        updateConfig = {}
      ),
    onUpgradeAccount = {}
  )
