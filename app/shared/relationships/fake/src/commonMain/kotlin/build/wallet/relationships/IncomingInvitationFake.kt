package build.wallet.relationships

import build.wallet.bitkey.relationships.IncomingInvitation
import build.wallet.bitkey.relationships.InvitationFake

val IncomingInvitationFake = IncomingInvitation(
  relationshipId = InvitationFake.relationshipId,
  code = InvitationFake.code,
  protectedCustomerEnrollmentPakeKey = ProtectedCustomerEnrollmentPakeKeyFake.publicKey,
  recoveryRelationshipRoles = InvitationFake.roles
)
