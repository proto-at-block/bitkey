package build.wallet.inheritance

import app.cash.turbine.Turbine
import build.wallet.bitkey.inheritance.BeneficiaryClaim.PendingClaim
import build.wallet.bitkey.inheritance.BeneficiaryPendingClaimFake
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
  var startClaimResult: Result<PendingClaim, Error> = Ok(BeneficiaryPendingClaimFake),
) : InheritanceService {
  private val defaultRelationships = Relationships(
    invitations = listOf(),
    endorsedTrustedContacts = listOf(EndorsedBeneficiaryFake),
    unendorsedTrustedContacts = listOf(),
    protectedCustomers = immutableListOf()
  )

  var invitation = InvitationFake

  override val pendingClaims = MutableStateFlow<Result<List<RelationshipId>, Error>?>(Ok(emptyList()))
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

  fun reset() {
    invitation = InvitationFake
    relationships.value = defaultRelationships
    pendingClaims.value = null
  }
}
