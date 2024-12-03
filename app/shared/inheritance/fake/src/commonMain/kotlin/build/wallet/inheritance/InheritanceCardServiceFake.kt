package build.wallet.inheritance

import build.wallet.bitkey.inheritance.BeneficiaryClaim
import kotlinx.coroutines.flow.MutableStateFlow

class InheritanceCardServiceFake : InheritanceCardService {
  override val claimCardsToDisplay = MutableStateFlow<List<BeneficiaryClaim>>(emptyList())

  override suspend fun dismissPendingClaimCard(claimId: String) {}

  fun reset() {
    claimCardsToDisplay.value = emptyList()
  }
}
