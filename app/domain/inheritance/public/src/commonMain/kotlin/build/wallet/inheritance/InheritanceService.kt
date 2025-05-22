package build.wallet.inheritance

import bitkey.relationships.Relationships
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Service for managing inheritance relationships and executing inheritance operations.
 */
interface InheritanceService {
/**
   * Emits latest [Relationships] stored in the database by filtering [RelationshipsService.relationships]
   * by the [TrustedContactRole.BENEFICIARY].
   *
   * Emits `null` on initial loading.
   * Emits [Relationships.EMPTY] if there was an error loading relationships from the database.
   */
  val inheritanceRelationships: Flow<Relationships>

  /**
   * Emits a collection of all claims.
   */
  val claims: Flow<List<InheritanceClaim>>

  /**
   * Emits a collection of the latest claims paired with the timestamp for
   * reference. This will update with a new timestamp if any claim passes
   * its approval date.
   */
  val claimsSnapshot: Flow<ClaimsSnapshot>

  /**
   * Snapshot of the current state of each beneficiary for the user.
   */
  val beneficiaryClaimState: Flow<ImmutableList<ContactClaimState.Beneficiary>>

  /**
   * Snapshot of the current state of each benefactor for the user.
   */
  val benefactorClaimState: Flow<ImmutableList<ContactClaimState.Benefactor>>

  /**
   * Creates an invitation for a Recovery Contact to become a beneficiary
   *
   * @param hardwareProofOfPossession the hardware proof of possession for creating the invitation
   * @param trustedContactAlias the alias of the beneficiary
   *
   * @return result either an [OutgoingInvitation] or an [Error]
   */
  suspend fun createInheritanceInvitation(
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
  ): Result<OutgoingInvitation, Error>

  /**
   * Uploads any inheritance material to the server for storage.
   *
   * This will only sync if the material is not already uploaded.
   */
  suspend fun syncInheritanceMaterial(keybox: Keybox): Result<Unit, Error>

  /**
   * Start an inheritance claim for a beneficiary.
   *
   * @param relationshipId the ID indicating the benefactor to start a claim for.
   */
  suspend fun startInheritanceClaim(
    relationshipId: RelationshipId,
  ): Result<BeneficiaryClaim.PendingClaim, Throwable>

  /**
   * Provides the details of an inheritance transfer.
   *
   * This call can only be invoked after the delay period has been completed
   * for a claim. This is because loading the details of the claim requires
   * locking the claim to receive the descriptor of the benefactor in order
   * to create a transaction.
   * If a claim has already been locked, further invocations of this method
   * will provide details on the existing locked claim.
   */
  suspend fun loadApprovedClaim(
    relationshipId: RelationshipId,
  ): Result<InheritanceTransactionDetails, Throwable>

  /**
   * Completes a claim transfer for a beneficiary by initiating the
   * inheritance transaction.
   */
  suspend fun completeClaimTransfer(
    relationshipId: RelationshipId,
    details: InheritanceTransactionDetails,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable>

  /**
   * Cancel any/all pending claims for the specified relationship.
   */
  suspend fun cancelClaims(relationshipId: RelationshipId): Result<Unit, Throwable>

  /**
   * Complete an inheritance claim that has no funds available to transfer.
   */
  suspend fun completeClaimWithoutTransfer(
    relationshipId: RelationshipId,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable>

  data class ClaimNotFoundError(
    override val message: String,
  ) : Error()
}

/**
 * Combined list of both the benefactor and beneficiary claim states.
 */
val InheritanceService.claimStates: Flow<ImmutableList<ContactClaimState>> get() {
  return combine(benefactorClaimState, beneficiaryClaimState) { benefactors, beneficiaries ->
    (benefactors + beneficiaries).toImmutableList()
  }
}
