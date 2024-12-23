package build.wallet.recovery.socrec

import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.relationships.RelationshipsCodeBuilder
import build.wallet.relationships.RelationshipsEnrollmentAuthenticationDao
import com.github.michaelbull.result.*

@BitkeyInject(AppScope::class)
class InviteCodeLoaderImpl(
  private val relationshipsEnrollmentAuthenticationDao: RelationshipsEnrollmentAuthenticationDao,
  private val recoveryCodeBuilder: RelationshipsCodeBuilder,
) : InviteCodeLoader {
  override suspend fun getInviteCode(invitation: Invitation): Result<OutgoingInvitation, Error> =
    relationshipsEnrollmentAuthenticationDao.getByRelationshipId(invitation.relationshipId)
      .mapError { Error("error loading pake data", it) }
      .toErrorIfNull {
        Error("missing pake data for ${invitation.relationshipId}")
      }
      .flatMap {
        recoveryCodeBuilder.buildInviteCode(invitation.code, invitation.codeBitLength, it.pakeCode)
      }
      .map { OutgoingInvitation(invitation, it) }
}
