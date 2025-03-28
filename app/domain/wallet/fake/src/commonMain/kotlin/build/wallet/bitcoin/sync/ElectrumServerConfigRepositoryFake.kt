package build.wallet.bitcoin.sync

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ElectrumServerConfigRepositoryFake : ElectrumServerConfigRepository {
  val f8eDefinedElectrumConfig = MutableStateFlow<ElectrumServer?>(null)
  val userElectrumServerPreferenceValue = MutableStateFlow<ElectrumServerPreferenceValue?>(null)

  override suspend fun storeF8eDefinedElectrumConfig(
    electrumServerDetails: ElectrumServerDetails,
  ): Result<Unit, Error> {
    f8eDefinedElectrumConfig.value = ElectrumServer.F8eDefined(electrumServerDetails)
    return Ok(Unit)
  }

  override suspend fun storeUserPreference(
    preference: ElectrumServerPreferenceValue,
  ): Result<Unit, Error> {
    userElectrumServerPreferenceValue.value = preference
    return Ok(Unit)
  }

  override fun getUserElectrumServerPreference(): Flow<ElectrumServerPreferenceValue?> =
    userElectrumServerPreferenceValue

  override fun getF8eDefinedElectrumServer(): Flow<ElectrumServer?> = f8eDefinedElectrumConfig

  fun reset() {
    f8eDefinedElectrumConfig.value = null
    userElectrumServerPreferenceValue.value = null
  }
}
