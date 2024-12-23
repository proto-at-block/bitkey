package build.wallet.inheritance

import app.cash.turbine.Turbine
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim.PendingClaim
import build.wallet.bitkey.inheritance.BeneficiaryCompleteClaimFake
import build.wallet.bitkey.inheritance.BeneficiaryPendingClaimFake
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.relationships.Relationships
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class InheritanceServiceMock(
  val syncCalls: Turbine<Keybox>,
  var syncResult: Result<Unit, Error> = Ok(Unit),
  var loadApprovedClaimResult: Result<InheritanceTransactionDetails, Error> =
    Ok(InheritanceTransactionDetailsFake),
  var completeClaimResult: Result<BeneficiaryClaim.CompleteClaim, Error> =
    Ok(BeneficiaryCompleteClaimFake),
  var startClaimResult: Result<PendingClaim, Error> = Ok(BeneficiaryPendingClaimFake),
) : InheritanceService {
  private val defaultRelationships = Relationships(
    invitations = listOf(),
    endorsedTrustedContacts = listOf(EndorsedBeneficiaryFake),
    unendorsedTrustedContacts = listOf(),
    protectedCustomers = immutableListOf()
  )

  var invitation = InvitationFake

  override val claims = MutableStateFlow<List<InheritanceClaim>>(emptyList())
  override val relationshipsWithPendingClaim = MutableStateFlow<List<RelationshipId>>(emptyList())
  override val relationshipsWithNoActiveClaims = MutableStateFlow<List<RelationshipId>>(emptyList())
  override val relationshipsWithCancelableClaim = MutableStateFlow<List<RelationshipId>>(emptyList())
  override val relationshipsWithCompletableClaim = MutableStateFlow<List<RelationshipId>>(emptyList())

  val relationships = MutableStateFlow(defaultRelationships)
  override val inheritanceRelationships = relationships

  override suspend fun createInheritanceInvitation(
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
  ): Result<OutgoingInvitation, Error> {
    return Ok(
      OutgoingInvitation(
        invitation = invitation,
        inviteCode = "fake-invite-code"
      )
    )
  }

  override suspend fun syncInheritanceMaterial(keybox: Keybox): Result<Unit, Error> {
    syncCalls.add(keybox)

    return syncResult
  }

  override suspend fun startInheritanceClaim(
    relationshipId: RelationshipId,
  ): Result<PendingClaim, Throwable> {
    return startClaimResult
  }

  override suspend fun loadApprovedClaim(
    relationshipId: RelationshipId,
  ): Result<InheritanceTransactionDetails, Throwable> {
    return loadApprovedClaimResult
  }

  override suspend fun completeClaimTransfer(
    relationshipId: RelationshipId,
    details: InheritanceTransactionDetails,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable> {
    return completeClaimResult
  }

  fun reset() {
    invitation = InvitationFake
    relationships.value = defaultRelationships
    relationshipsWithPendingClaim.value = emptyList()
    claims.value = emptyList()
  }
}
