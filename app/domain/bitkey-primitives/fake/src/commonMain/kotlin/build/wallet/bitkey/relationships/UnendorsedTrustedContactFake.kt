package build.wallet.bitkey.relationships

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

val UnendorsedBeneficiaryFake = UnendorsedTrustedContactFake.copy(
  relationshipId = "unendorsedBeneficiaryRelationshipId",
  trustedContactAlias = TrustedContactAlias("unendorsedBeneficiaryAlias"),
  roles = setOf(TrustedContactRole.Beneficiary)
)
