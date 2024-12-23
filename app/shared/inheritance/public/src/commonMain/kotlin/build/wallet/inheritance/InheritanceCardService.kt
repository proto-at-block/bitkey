package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceClaim
import kotlinx.coroutines.flow.Flow

/**
 * Service for managing which inheritance claim cards should be displayed to the user.
 */
interface InheritanceCardService {
  /**
   * Emits a collection of inheritance cards that should be displayed to the user.
   */
  val cardsToDisplay: Flow<List<InheritanceClaim>>

  /**
   * Dismisses a pending beneficiary claim card. User will no longer see the pending claim card
   * until the claim changes to the locked state
   */
  suspend fun dismissPendingBeneficiaryClaimCard(claimId: String)
}
