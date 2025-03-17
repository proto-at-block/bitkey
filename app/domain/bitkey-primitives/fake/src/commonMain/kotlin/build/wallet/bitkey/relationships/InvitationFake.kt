package build.wallet.bitkey.relationships

import kotlinx.datetime.Instant
import kotlin.random.Random

val InvitationFake =
  Invitation(
    relationshipId = "recoveryRelationshipId",
    trustedContactAlias = TrustedContactAlias("trustedContactAlias fake"),
    code = Random.nextBytes(3).toHexString(),
    codeBitLength = 20,
    expiresAt = Instant.DISTANT_FUTURE,
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )

val BeneficiaryInvitationFake = InvitationFake.copy(
  roles = setOf(TrustedContactRole.Beneficiary)
)
