package build.wallet.recovery.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.RecoveryContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.f8e.auth.HwFactorProofOfPossession

/**
 * Encapsulates actions protected customers can take that can produce side effects.
 */
class SocRecFullAccountActions internal constructor(
  private val repository: SocRecRelationshipsRepository,
  private val account: FullAccount,
) : SocRecLiteAccountActions(repository, account) {
  /**
   * @see SocRecRelationshipsRepository.createInvitation
   */
  suspend fun createInvitation(
    trustedContactAlias: TrustedContactAlias,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ) = repository.createInvitation(
    account = account,
    trustedContactAlias = trustedContactAlias,
    hardwareProofOfPossession = hardwareProofOfPossession
  )

  /**
   * @see SocRecRelationshipsRepository.refreshInvitation
   */
  suspend fun refreshInvitation(
    invitation: Invitation,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ) = repository.refreshInvitation(
    account = account,
    relationshipId = invitation.recoveryRelationshipId,
    hardwareProofOfPossession = hardwareProofOfPossession
  )

  /**
   * @see SocRecRelationshipsRepository.removeRelationship
   */
  suspend fun removeTrustedContact(
    contact: RecoveryContact,
    hardwareProofOfPossession: HwFactorProofOfPossession?,
  ) = repository.removeRelationship(
    account = account,
    hardwareProofOfPossession = hardwareProofOfPossession,
    authTokenScope = AuthTokenScope.Global,
    relationshipId = contact.recoveryRelationshipId
  )
}
