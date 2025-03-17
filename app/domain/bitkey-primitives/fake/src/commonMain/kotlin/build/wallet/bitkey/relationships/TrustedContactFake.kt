package build.wallet.bitkey.relationships

import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED

val EndorsedTrustedContactFake1 =
  EndorsedTrustedContact(
    relationshipId = "someRelationshipId",
    trustedContactAlias = TrustedContactAlias("someContact"),
    authenticationState = VERIFIED,
    keyCertificate = TrustedContactKeyCertificateFake,
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )

val EndorsedTrustedContactFake2 =
  EndorsedTrustedContactFake1.copy(relationshipId = "someOtherRelationshipId")

val EndorsedBeneficiaryFake = EndorsedTrustedContactFake1.copy(
  relationshipId = "endorsedBeneficiaryRelationshipId",
  trustedContactAlias = TrustedContactAlias("endorsedBeneficiaryAlias"),
  roles = setOf(TrustedContactRole.Beneficiary)
)
