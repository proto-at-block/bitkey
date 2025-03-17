package build.wallet.relationships

import build.wallet.bitkey.relationships.BeneficiaryInvitationFake
import build.wallet.bitkey.relationships.IncomingInvitation
import build.wallet.bitkey.relationships.InvitationFake

val IncomingRecoveryContactInvitationFake = IncomingInvitation(
  relationshipId = InvitationFake.relationshipId,
  code = InvitationFake.code,
  protectedCustomerEnrollmentPakeKey = ProtectedCustomerEnrollmentPakeKeyFake.publicKey,
  recoveryRelationshipRoles = InvitationFake.roles
)

val IncomingBeneficiaryInvitationFake = IncomingRecoveryContactInvitationFake.copy(
  relationshipId = BeneficiaryInvitationFake.relationshipId,
  code = BeneficiaryInvitationFake.code,
  protectedCustomerEnrollmentPakeKey = ProtectedCustomerEnrollmentPakeKeyFake.publicKey,
  recoveryRelationshipRoles = BeneficiaryInvitationFake.roles
)
