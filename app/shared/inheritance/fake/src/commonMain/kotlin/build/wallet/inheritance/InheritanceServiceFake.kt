package build.wallet.inheritance

import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.socrec.InvitationFake
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class InheritanceServiceFake : InheritanceService {
  var invitation = InvitationFake

  override suspend fun createInheritanceInvitation(
    hardwareProofOfPossession: HwFactorProofOfPossession,
    trustedContactAlias: TrustedContactAlias,
  ): Result<OutgoingInvitation, Error> {
    return Ok(
      OutgoingInvitation(
        invitation = invitation,
        inviteCode = "fake-invite-code"
      )
    )
  }
}
