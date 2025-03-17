package build.wallet.relationships

import build.wallet.bitkey.relationships.InvitationFake
import build.wallet.bitkey.relationships.OutgoingInvitation

val OutgoingInvitationFake = OutgoingInvitation(
  invitation = InvitationFake,
  inviteCode = InvitationFake.code + "123456"
)
