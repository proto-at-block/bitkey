package build.wallet.bitcoin.lightning

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class LightningPreferenceMock(
  var getResult: Boolean,
) : LightningPreference {
  override suspend fun get(): Boolean {
    return getResult
  }

  override suspend fun set(enabled: Boolean) {}

  override fun isEnabled(): Flow<Boolean> {
    return flowOf(getResult)
  }
}
