package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.statemachine.data.account.create.LoadedOnboardConfigDataMock
import build.wallet.statemachine.data.keybox.AccountData.HasActiveLiteAccountData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData

val HasActiveLiteAccountDataFake =
  HasActiveLiteAccountData(
    account = LiteAccountMock,
    accountUpgradeOnboardConfigData = LoadedOnboardConfigDataMock,
    accountUpgradeTemplateFullAccountConfigData =
      LoadedTemplateFullAccountConfigData(
        config = FullAccountConfigMock,
        updateConfig = {}
      ),
    onUpgradeAccount = {}
  )
