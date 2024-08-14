package build.wallet.bitcoin.sync

import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.Off
import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue.On
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class ElectrumConfigServiceFake : ElectrumConfigService {
  val electrumServerPreference = MutableStateFlow<ElectrumServerPreferenceValue?>(OffElectrumServerPreferenceValueMock)

  override fun electrumServerPreference() = electrumServerPreference

  override suspend fun disableCustomElectrumServer(): Result<Unit, Error> {
    when (val preference = electrumServerPreference.value) {
      is Off, null -> Unit
      is On -> {
        electrumServerPreference.value = Off(previousUserDefinedElectrumServer = preference.server)
      }
    }
    return Ok(Unit)
  }

  fun reset() {
    electrumServerPreference.value = OffElectrumServerPreferenceValueMock
  }
}
