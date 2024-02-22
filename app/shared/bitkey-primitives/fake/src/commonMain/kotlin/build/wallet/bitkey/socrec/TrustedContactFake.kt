package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey

val TrustedContactFake1 =
  TrustedContact(
    recoveryRelationshipId = "someRelationshipId",
    trustedContactAlias = TrustedContactAlias("someContact"),
    identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("deadbeef"))
  )

val TrustedContactFake2 =
  TrustedContact(
    recoveryRelationshipId = "someOtherRelationshipId",
    trustedContactAlias = TrustedContactAlias("someContact"),
    identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("deadbeef"))
  )
