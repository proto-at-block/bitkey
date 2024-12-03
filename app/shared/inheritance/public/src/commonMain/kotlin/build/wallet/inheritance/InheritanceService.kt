package build.wallet.inheritance

import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.relationships.Relationships
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
  val inheritanceRelationships: StateFlow<Relationships?>

  /**
   * Emits a collection of relationships with currently pending claims.
   */
  val pendingClaims: StateFlow<Result<List<RelationshipId>, Error>?>

  /**
   * Emits a collection of synced pending beneficiary claims.
   */
  val pendingBeneficiaryClaims: Flow<List<BeneficiaryClaim.PendingClaim>>

  /**
   * Emits a collection of locked beneficiary claims.
   */
  val lockedBeneficiaryClaims: Flow<List<BeneficiaryClaim>>

  /**
   * Creates an invitation for a trusted contact to become a beneficiary
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
}
