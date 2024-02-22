package build.wallet.bitcoin.sync

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN

val DefaultServerSettingMock =
  ElectrumServerSetting.Default(
    ElectrumServer.Mempool(BITCOIN)
  )

val DefaultServerSettingWithPreviousServerMock =
  ElectrumServerSetting.Default(
    ElectrumServer.Mempool(BITCOIN)
  )

val UserDefinedServerSettingMock =
  ElectrumServerSetting.UserDefined(
    server = CustomElectrumServerMock
  )
