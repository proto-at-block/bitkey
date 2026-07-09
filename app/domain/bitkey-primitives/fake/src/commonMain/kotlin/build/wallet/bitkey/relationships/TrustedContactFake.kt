package build.wallet.bitkey.relationships

import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED

val EndorsedTrustedContactFake1 =
  EndorsedTrustedContact(
    id = RelationshipId("someRelationshipId"),
    trustedContactAlias = TrustedContactAlias("someContact"),
    authenticationState = VERIFIED,
    keyCertificate = TrustedContactKeyCertificateFake,
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )

val EndorsedTrustedContactFake2 =
  EndorsedTrustedContactFake1.copy(id = RelationshipId("someOtherRelationshipId"))

val EndorsedBeneficiaryFake = EndorsedTrustedContactFake1.copy(
  id = RelationshipId("endorsedBeneficiaryRelationshipId"),
  trustedContactAlias = TrustedContactAlias("endorsedBeneficiaryAlias"),
  roles = setOf(TrustedContactRole.Beneficiary)
)
