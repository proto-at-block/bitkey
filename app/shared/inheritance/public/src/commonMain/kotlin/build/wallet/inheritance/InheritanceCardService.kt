package build.wallet.inheritance

import build.wallet.bitkey.inheritance.BeneficiaryClaim
import kotlinx.coroutines.flow.Flow

/**
 * Service for managing which inheritance claim cards should be displayed to the user.
 */
interface InheritanceCardService {
  /**
   * Emits a collection of beneficiary claims that should be displayed to the user.
   */
  val claimCardsToDisplay: Flow<List<BeneficiaryClaim>>

  /**
   * Dismisses a pending claim card. User will no longer see the pending claim card
   */
  suspend fun dismissPendingClaimCard(claimId: String)
}
