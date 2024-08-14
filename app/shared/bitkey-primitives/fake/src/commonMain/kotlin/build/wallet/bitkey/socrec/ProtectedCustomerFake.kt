package build.wallet.bitkey.socrec

import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
import build.wallet.bitkey.relationships.TrustedContactRole

val ProtectedCustomerFake =
  ProtectedCustomer(
    relationshipId = "recoveryRelationshipId-fake",
    alias = ProtectedCustomerAlias("protected customer alias fake"),
    roles = setOf(TrustedContactRole.SocialRecoveryContact)
  )
