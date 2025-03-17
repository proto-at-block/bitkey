package build.wallet.feature

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import kotlinx.coroutines.CoroutineScope

class FeatureFlagSyncerMock(
  turbine: (String) -> Turbine<Any>,
) : FeatureFlagSyncer {
  val initializeSyncLoopCalls = turbine("initializeSyncLoop calls")
  val syncCalls = turbine("sync calls")

  override suspend fun initializeSyncLoop(scope: CoroutineScope) {
    initializeSyncLoopCalls += Unit
  }

  override suspend fun sync() {
    syncCalls += Unit
  }
}
