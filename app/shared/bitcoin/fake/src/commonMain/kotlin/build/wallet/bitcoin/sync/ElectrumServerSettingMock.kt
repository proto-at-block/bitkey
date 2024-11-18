package build.wallet.bitcoin.sync

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN

val DefaultServerSettingMock =
  ElectrumServerSetting.Default(
    ElectrumServer.Mempool(BITCOIN, isAndroidEmulator = false)
  )

val DefaultServerSettingWithPreviousServerMock =
  ElectrumServerSetting.Default(
    ElectrumServer.Mempool(BITCOIN, isAndroidEmulator = false)
  )

val UserDefinedServerSettingMock =
  ElectrumServerSetting.UserDefined(
    server = CustomElectrumServerMock
  )
