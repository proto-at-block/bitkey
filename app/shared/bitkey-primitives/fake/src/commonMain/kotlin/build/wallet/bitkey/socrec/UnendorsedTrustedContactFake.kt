package build.wallet.bitkey.socrec

import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import okio.ByteString.Companion.encodeUtf8

val UnendorsedTrustedContactFake = UnendorsedTrustedContact(
  relationshipId = "someRelationshipId",
  trustedContactAlias = TrustedContactAlias("someContact"),
  enrollmentPakeKey = PublicKey("deadbeef"),
  enrollmentKeyConfirmation = "".encodeUtf8(),
  sealedDelegatedDecryptionKey = XCiphertext("deadbeef"),
  authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED,
  roles = setOf(TrustedContactRole.SocialRecoveryContact)
)
