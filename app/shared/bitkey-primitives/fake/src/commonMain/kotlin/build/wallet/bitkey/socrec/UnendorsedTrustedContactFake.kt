package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey

val UnendorsedTrustedContactFake = UnendorsedTrustedContact(
  recoveryRelationshipId = "someRelationshipId",
  trustedContactAlias = TrustedContactAlias("someContact"),
  identityKey = TrustedContactIdentityKey(AppKey.fromPublicKey("deadbeef")),
  identityPublicKeyMac = "",
  enrollmentKey = TrustedContactEnrollmentKey(AppKey.fromPublicKey("deadbeef")),
  enrollmentKeyConfirmation = "",
  authenticationState = TrustedContactAuthenticationState.UNENDORSED
)
