package build.wallet.bitkey.socrec

import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.VERIFIED

val TrustedContactFake1 =
  TrustedContact(
    recoveryRelationshipId = "someRelationshipId",
    trustedContactAlias = TrustedContactAlias("someContact"),
    authenticationState = VERIFIED,
    keyCertificate = TrustedContactKeyCertificateFake
  )

val TrustedContactFake2 =
  TrustedContactFake1.copy(recoveryRelationshipId = "someOtherRelationshipId")
