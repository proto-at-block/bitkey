package build.wallet.bitkey.socrec

import kotlinx.datetime.Instant
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
val InvitationFake =
  Invitation(
    recoveryRelationshipId = "recoveryRelationshipId",
    trustedContactAlias = TrustedContactAlias("trustedContactAlias fake"),
    token = Random.nextBytes(32).toHexString(),
    expiresAt = Instant.DISTANT_FUTURE
  )
