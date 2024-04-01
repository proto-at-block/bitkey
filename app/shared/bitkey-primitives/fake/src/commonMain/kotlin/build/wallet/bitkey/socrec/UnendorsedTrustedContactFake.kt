package build.wallet.bitkey.socrec

import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import okio.ByteString.Companion.encodeUtf8

val UnendorsedTrustedContactFake = UnendorsedTrustedContact(
  recoveryRelationshipId = "someRelationshipId",
  trustedContactAlias = TrustedContactAlias("someContact"),
  enrollmentPakeKey = PublicKey("deadbeef"),
  enrollmentKeyConfirmation = "".encodeUtf8(),
  sealedDelegatedDecryptionKey = XCiphertext("deadbeef"),
  authenticationState = TrustedContactAuthenticationState.UNAUTHENTICATED
)
