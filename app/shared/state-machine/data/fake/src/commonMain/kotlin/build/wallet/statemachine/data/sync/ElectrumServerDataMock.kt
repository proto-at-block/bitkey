package build.wallet.statemachine.data.sync

import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.sync.ElectrumServer.F8eDefined
import build.wallet.bitcoin.sync.ElectrumServer.Mempool
import build.wallet.bitcoin.sync.ElectrumServerDetails
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue

val PlaceholderElectrumServerDataMock =
  ElectrumServerData(
    defaultElectrumServer = Mempool(SIGNET),
    userDefinedElectrumServerPreferenceValue =
      ElectrumServerPreferenceValue.Off(
        previousUserDefinedElectrumServer = null
      ),
    disableCustomElectrumServer = {}
  )

val F8eDefinedElectrumServerDataMock =
  ElectrumServerData(
    defaultElectrumServer = F8eDefined(ElectrumServerDetails("chicken.info", "1234")),
    userDefinedElectrumServerPreferenceValue =
      ElectrumServerPreferenceValue.Off(
        previousUserDefinedElectrumServer = null
      ),
    disableCustomElectrumServer = {}
  )
