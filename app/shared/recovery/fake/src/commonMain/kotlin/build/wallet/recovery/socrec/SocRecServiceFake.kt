package build.wallet.recovery.socrec

import build.wallet.f8e.relationships.RelationshipsFake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class SocRecServiceFake : SocRecService {
  override val socRecRelationships = MutableStateFlow(RelationshipsFake)

  override fun justCompletedRecovery() = flowOf(false)

  fun reset() {
    socRecRelationships.value = RelationshipsFake
  }
}
