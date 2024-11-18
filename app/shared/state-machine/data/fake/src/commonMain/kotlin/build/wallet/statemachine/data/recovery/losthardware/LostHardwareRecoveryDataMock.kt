package build.wallet.statemachine.data.recovery.losthardware

import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock

val LostHardwareRecoveryDataMock =
  LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData(
    newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
    addHardwareKeys = { _, _, _ -> }
  )
