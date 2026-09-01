package build.wallet.bitkey.relationships

val ProtectedCustomerFake = ProtectedCustomer(
  id = RelationshipId("recoveryRelationshipId-fake"),
  alias = ProtectedCustomerAlias("protected customer alias fake"),
  roles = setOf(TrustedContactRole.SocialRecoveryContact)
)

val UnendorsedProtectedCustomerFake = ProtectedCustomer(
  id = RelationshipId("unendorsedRecoveryRelationshipId-fake"),
  alias = ProtectedCustomerAlias("pending protected customer alias fake"),
  roles = setOf(TrustedContactRole.SocialRecoveryContact),
  relationshipStatus = ProtectedCustomerRelationshipStatus.UNENDORSED
)

val ProtectedBeneficiaryCustomerFake =
  ProtectedCustomer(
    id = RelationshipId("beneficiaryPCRelationshipIc"),
    alias = ProtectedCustomerAlias("beneficiaryPC"),
    roles = setOf(TrustedContactRole.Beneficiary)
  )
