package build.wallet.recovery.socrec

import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.socrec.InvitationFake

val OutgoingInvitationFake = OutgoingInvitation(
  invitation = InvitationFake,
  inviteCode = InvitationFake.code + "123456"
)
