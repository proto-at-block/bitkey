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
  override suspend fun getInviteCode(
    invitation: Invitation,
  ): Result<OutgoingInvitation, InviteCodeLoadError> =
    relationshipsEnrollmentAuthenticationDao.getByRelationshipId(invitation.id.value)
      .mapError<_, _, InviteCodeLoadError> { InviteCodeLoadError.StorageError(it) }
      .toErrorIfNull {
        InviteCodeLoadError.MissingPakeData(invitation.id.value)
      }
      .flatMap {
        recoveryCodeBuilder.buildInviteCode(invitation.code, invitation.codeBitLength, it.pakeCode)
          .mapError { err -> InviteCodeLoadError.EncodingError(err) }
      }
      .map { OutgoingInvitation(invitation, it) }
}
