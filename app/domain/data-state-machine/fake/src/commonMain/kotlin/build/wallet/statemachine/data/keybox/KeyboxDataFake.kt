package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

val ActiveKeyboxLoadedDataMock = ActiveFullAccountLoadedData(
  account = FullAccountMock
)
