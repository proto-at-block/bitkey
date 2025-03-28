package build.wallet.bitcoin.sync

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitcoin.sync.ElectrumServerSetting.UserDefined
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class ElectrumServerSettingProviderMock(
  val turbine: (name: String) -> Turbine<Any>,
  val initialSetting: ElectrumServerSetting = DefaultServerSettingMock,
) : ElectrumServerSettingProvider {
  val electrumServerSetting = MutableStateFlow(initialSetting)

  override fun get(): Flow<ElectrumServerSetting> = electrumServerSetting

  val setCalls = turbine("set electrum server pref calls")

  override suspend fun setUserDefinedServer(server: ElectrumServer) {
    setCalls += server
    electrumServerSetting.value = UserDefined(server)
  }

  val clearCalls = turbine("clear electrum server pref calls")

  fun reset() {
    electrumServerSetting.value = initialSetting
  }
}
