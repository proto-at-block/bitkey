package build.wallet.bitcoin.sync

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ElectrumServerConfigRepositoryMock(
  turbine: (String) -> Turbine<Any>,
) : ElectrumServerConfigRepository {
  var storeUserPreferenceCalls = turbine("store user Electrum server preference dao calls")
  var storeF8eDefinedServerCalls = turbine("store user Electrum server returned by f8e calls")

  var f8eDefinedElectrumConfig = MutableStateFlow<ElectrumServer?>(null)
  var userElectrumServerPreferenceValue = MutableStateFlow<ElectrumServerPreferenceValue?>(null)

  override suspend fun storeF8eDefinedElectrumConfig(
    electrumServerDetails: ElectrumServerDetails,
  ): Result<Unit, DbError> {
    storeF8eDefinedServerCalls += electrumServerDetails
    return Ok(Unit)
  }

  override suspend fun storeUserPreference(
    preference: ElectrumServerPreferenceValue,
  ): Result<Unit, DbError> {
    storeUserPreferenceCalls += preference
    return Ok(Unit)
  }

  override fun getUserElectrumServerPreference(): Flow<ElectrumServerPreferenceValue?> =
    userElectrumServerPreferenceValue

  override fun getF8eDefinedElectrumServer(): Flow<ElectrumServer?> = f8eDefinedElectrumConfig
}
