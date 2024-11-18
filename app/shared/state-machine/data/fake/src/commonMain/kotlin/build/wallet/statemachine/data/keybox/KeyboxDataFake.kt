package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock

val ActiveKeyboxLoadedDataMock = ActiveFullAccountLoadedData(
  account = FullAccountMock,
  lostHardwareRecoveryData = LostHardwareRecoveryDataMock
)
