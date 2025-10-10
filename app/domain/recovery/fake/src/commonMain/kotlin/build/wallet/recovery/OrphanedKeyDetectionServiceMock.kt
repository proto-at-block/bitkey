package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OrphanedKeyDetectionServiceMock(
  turbine: (String) -> Turbine<Any>,
) : OrphanedKeyDetectionService {
  val detectCalls = turbine("detect calls")
  val orphanedKeysStateFlow = MutableStateFlow<OrphanedKeysState>(OrphanedKeysState.NoOrphanedKeys)

  override fun orphanedKeysState(): StateFlow<OrphanedKeysState> = orphanedKeysStateFlow

  override suspend fun detect(): OrphanedKeysState {
    detectCalls += Unit
    return orphanedKeysStateFlow.value
  }

  fun reset() {
    orphanedKeysStateFlow.value = OrphanedKeysState.NoOrphanedKeys
  }
}
