package build.wallet.bitcoin.sync

import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN

val DefaultElectrumServerMock = ElectrumServer.Mempool(
  network = BITCOIN,
  isAndroidEmulator = false
)
val CustomElectrumServerMock =
  ElectrumServer.Custom(
    ElectrumServerDetails(
      host = "chicken.info",
      port = "50002"
    )
  )
val F8eDefinedElectrumServerMock =
  ElectrumServer.F8eDefined(
    ElectrumServerDetails(
      host = "duck.info",
      port = "50002"
    )
  )
