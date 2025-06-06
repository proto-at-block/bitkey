package bitkey.recovery.fundslost

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FundsLostRiskServiceFake : FundsLostRiskService, FundsLostRiskSyncWorker {
  val riskLevel = MutableStateFlow<FundsLostRiskLevel>(FundsLostRiskLevel.Protected)

  override fun riskLevel(): StateFlow<FundsLostRiskLevel> {
    return riskLevel
  }

  fun reset() {
    riskLevel.value = FundsLostRiskLevel.Protected
  }

  override suspend fun executeWork() {
    // no-op for fake implementation
  }
}
