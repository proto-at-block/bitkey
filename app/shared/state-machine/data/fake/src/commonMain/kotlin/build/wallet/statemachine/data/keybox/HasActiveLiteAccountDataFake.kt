package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.statemachine.data.keybox.AccountData.HasActiveLiteAccountData

val HasActiveLiteAccountDataFake =
  HasActiveLiteAccountData(
    account = LiteAccountMock,
    onUpgradeAccount = {}
  )
