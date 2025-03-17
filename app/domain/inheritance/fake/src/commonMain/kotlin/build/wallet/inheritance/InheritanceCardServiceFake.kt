package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.inheritance.InheritanceClaimId
import kotlinx.coroutines.flow.MutableStateFlow

class InheritanceCardServiceFake : InheritanceCardService {
  override val cardsToDisplay = MutableStateFlow<List<InheritanceClaim>>(emptyList())

  override suspend fun dismissPendingBeneficiaryClaimCard(claimId: InheritanceClaimId) {}

  fun reset() {
    cardsToDisplay.value = emptyList()
  }
}
