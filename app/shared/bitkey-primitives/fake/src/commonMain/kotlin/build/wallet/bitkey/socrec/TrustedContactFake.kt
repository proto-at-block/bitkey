package build.wallet.bitkey.socrec

import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.bitkey.relationships.TrustedContactRole

val EndorsedTrustedContactFake1 =
  EndorsedTrustedContact(
    relationshipId = "someRelationshipId",
    trustedContactAlias = TrustedContactAlias("someContact"),
    authenticationState = VERIFIED,
    keyCertificate = TrustedContactKeyCertificateFake,
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )

val TrustedContactFake2 =
  EndorsedTrustedContactFake1.copy(relationshipId = "someOtherRelationshipId")
