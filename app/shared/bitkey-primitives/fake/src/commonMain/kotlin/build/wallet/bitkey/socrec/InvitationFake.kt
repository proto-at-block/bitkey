package build.wallet.bitkey.socrec

import kotlinx.datetime.Instant
import kotlin.random.Random

val InvitationFake =
  Invitation(
    recoveryRelationshipId = "recoveryRelationshipId",
    trustedContactAlias = TrustedContactAlias("trustedContactAlias fake"),
    code = Random.nextBytes(3).toHexString(),
    codeBitLength = 20,
    expiresAt = Instant.DISTANT_FUTURE
  )
