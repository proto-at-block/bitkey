package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.IncomingInvitation
import build.wallet.bitkey.socrec.InvitationFake

val IncomingInvitationFake = IncomingInvitation(
  recoveryRelationshipId = InvitationFake.recoveryRelationshipId,
  code = InvitationFake.code,
  protectedCustomerEnrollmentPakeKey = ProtectedCustomerEnrollmentPakeKeyFake
)
