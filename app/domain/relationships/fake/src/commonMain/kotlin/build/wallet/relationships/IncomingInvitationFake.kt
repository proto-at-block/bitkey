package build.wallet.relationships

import build.wallet.bitkey.relationships.BeneficiaryInvitationFake
import build.wallet.bitkey.relationships.IncomingInvitation
import build.wallet.bitkey.relationships.InvitationFake

val IncomingRecoveryContactInvitationFake = IncomingInvitation(
  id = InvitationFake.id,
  code = InvitationFake.code,
  protectedCustomerEnrollmentPakeKey = ProtectedCustomerEnrollmentPakeKeyFake.publicKey,
  recoveryRelationshipRoles = InvitationFake.roles,
  expiresAt = InvitationFake.expiresAt
)

val IncomingBeneficiaryInvitationFake = IncomingRecoveryContactInvitationFake.copy(
  id = BeneficiaryInvitationFake.id,
  code = BeneficiaryInvitationFake.code,
  protectedCustomerEnrollmentPakeKey = ProtectedCustomerEnrollmentPakeKeyFake.publicKey,
  recoveryRelationshipRoles = BeneficiaryInvitationFake.roles,
  expiresAt = BeneficiaryInvitationFake.expiresAt
)
