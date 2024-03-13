package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.OutgoingInvitation
import build.wallet.bitkey.socrec.PakeCode
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull

class InviteCodeLoaderImpl(
  private val socRecEnrollmentAuthenticationDao: SocRecEnrollmentAuthenticationDao,
  private val recoveryCodeBuilder: SocialRecoveryCodeBuilder,
) : InviteCodeLoader {
  override suspend fun getInviteCode(invitation: Invitation): Result<OutgoingInvitation, Error> =
    socRecEnrollmentAuthenticationDao.getByRelationshipId(invitation.recoveryRelationshipId)
      .mapError { Error("error loading pake data", it) }
      .toErrorIfNull {
        Error("missing pake data for ${invitation.recoveryRelationshipId}")
      }
      .flatMap {
        recoveryCodeBuilder.buildInviteCode(invitation.code, invitation.codeBitLength, PakeCode(it.pakeCode))
      }
      .map { OutgoingInvitation(invitation, it) }
}
