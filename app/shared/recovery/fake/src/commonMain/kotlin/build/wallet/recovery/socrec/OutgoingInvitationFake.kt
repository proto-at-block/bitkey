package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.InvitationFake
import build.wallet.bitkey.socrec.OutgoingInvitation

val OutgoingInvitationFake = OutgoingInvitation(
  invitation = InvitationFake,
  inviteCode = InvitationFake.code + "123456"
)
