package build.wallet.bitkey.relationships

val ProtectedCustomerFake = ProtectedCustomer(
  relationshipId = "recoveryRelationshipId-fake",
  alias = ProtectedCustomerAlias("protected customer alias fake"),
  roles = setOf(TrustedContactRole.SocialRecoveryContact)
)

val ProtectedBeneficiaryCustomerFake =
  ProtectedCustomer(
    relationshipId = "beneficiaryPCRelationshipIc",
    alias = ProtectedCustomerAlias("beneficiaryPC"),
    roles = setOf(TrustedContactRole.Beneficiary)
  )
