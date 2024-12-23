package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceClaim
import kotlinx.coroutines.flow.MutableStateFlow

class InheritanceCardServiceFake : InheritanceCardService {
  override val cardsToDisplay = MutableStateFlow<List<InheritanceClaim>>(emptyList())

  override suspend fun dismissPendingBeneficiaryClaimCard(claimId: String) {}

  fun reset() {
    cardsToDisplay.value = emptyList()
  }
}
