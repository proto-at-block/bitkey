package build.wallet.bitkey.socrec

import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.VERIFIED

val EndorsedTrustedContactFake1 =
  EndorsedTrustedContact(
    recoveryRelationshipId = "someRelationshipId",
    trustedContactAlias = TrustedContactAlias("someContact"),
    authenticationState = VERIFIED,
    keyCertificate = TrustedContactKeyCertificateFake
  )

val TrustedContactFake2 =
  EndorsedTrustedContactFake1.copy(recoveryRelationshipId = "someOtherRelationshipId")
