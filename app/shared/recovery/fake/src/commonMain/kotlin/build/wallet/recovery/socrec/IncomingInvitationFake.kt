package build.wallet.recovery.socrec

import build.wallet.bitkey.relationships.IncomingInvitation
import build.wallet.bitkey.socrec.InvitationFake

val IncomingInvitationFake = IncomingInvitation(
  relationshipId = InvitationFake.relationshipId,
  code = InvitationFake.code,
  protectedCustomerEnrollmentPakeKey = ProtectedCustomerEnrollmentPakeKeyFake.publicKey
)
