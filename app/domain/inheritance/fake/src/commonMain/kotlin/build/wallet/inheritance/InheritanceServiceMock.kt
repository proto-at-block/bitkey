package build.wallet.inheritance

import app.cash.turbine.Turbine
import bitkey.relationships.Relationships
import build.wallet.bitkey.inheritance.*
import build.wallet.bitkey.inheritance.BeneficiaryClaim.PendingClaim
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.*
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class InheritanceServiceMock(
  val syncCalls: Turbine<Keybox>,
  var syncResult: Result<Unit, Error> = Ok(Unit),
  var loadApprovedClaimResult: Result<InheritanceTransactionDetails, Error> =
    Ok(InheritanceTransactionDetailsFake),
  var completeClaimResult: Result<BeneficiaryClaim.CompleteClaim, Error> =
    Ok(BeneficiaryCompleteClaimFake),
  var startClaimResult: Result<PendingClaim, Error> = Ok(BeneficiaryPendingClaimFake),
  var cancelClaimResult: Result<Unit, Error> = Ok(Unit),
  val cancelClaimCalls: Turbine<Unit>? = null,
) : InheritanceService {
  private val defaultRelationships = Relationships(
    invitations = listOf(),
    endorsedTrustedContacts = listOf(),
    unendorsedTrustedContacts = listOf(),
    protectedCustomers = immutableListOf()
  )

  var invitation = InvitationFake

  override val claims = MutableStateFlow<List<InheritanceClaim>>(emptyList())
  override val claimsSnapshot = MutableStateFlow(ClaimsSnapshot(Instant.DISTANT_PAST, InheritanceClaims.EMPTY))
  override val beneficiaryClaimState = MutableStateFlow<ImmutableList<ContactClaimState.Beneficiary>>(emptyImmutableList())
  override val benefactorClaimState = MutableStateFlow<ImmutableList<ContactClaimState.Benefactor>>(emptyImmutableList())

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

  override suspend fun cancelClaims(relationshipId: RelationshipId): Result<Unit, Throwable> {
    cancelClaimCalls?.add(Unit)
    return cancelClaimResult
  }

  override suspend fun completeClaimTransfer(
    relationshipId: RelationshipId,
    details: InheritanceTransactionDetails,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable> {
    return completeClaimResult
  }

  override suspend fun completeClaimWithoutTransfer(
    relationshipId: RelationshipId,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable> {
    return completeClaimResult
  }

  fun reset() {
    invitation = InvitationFake
    relationships.value = defaultRelationships
  }

  /**
   * Helper method to simulate active inheritance by adding an endorsed beneficiary
   */
  fun setHasActiveInheritance(active: Boolean) {
    relationships.value = if (active) {
      Relationships(
        invitations = listOf(),
        endorsedTrustedContacts = listOf(EndorsedBeneficiaryFake),
        unendorsedTrustedContacts = listOf(),
        protectedCustomers = immutableListOf()
      )
    } else {
      defaultRelationships
    }
  }
}
