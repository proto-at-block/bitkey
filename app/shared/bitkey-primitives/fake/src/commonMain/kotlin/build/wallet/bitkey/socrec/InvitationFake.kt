package build.wallet.bitkey.socrec

import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
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
