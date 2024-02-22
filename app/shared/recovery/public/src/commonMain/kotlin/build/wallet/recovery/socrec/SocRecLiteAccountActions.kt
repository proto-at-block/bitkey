package build.wallet.recovery.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import com.github.michaelbull.result.Result

/**
 * Encapsulates actions trusted contacts can take that can produce side effects.
 */
open class SocRecLiteAccountActions internal constructor(
  private val repository: SocRecRelationshipsRepository,
  private val account: Account,
) {
  /**
   * @see SocRecRelationshipsRepository.removeRelationship
   */
  suspend fun removeProtectedCustomer(customer: ProtectedCustomer) =
    repository.removeRelationship(
      account = account,
      hardwareProofOfPossession = null,
      authTokenScope = AuthTokenScope.Recovery,
      relationshipId = customer.recoveryRelationshipId
    )

  /**
   * @see SocRecRelationshipsRepository.retrieveInvitation
   */
  suspend fun retrieveInvitation(
    invitationCode: String,
  ): Result<Invitation, F8eError<RetrieveTrustedContactInvitationErrorCode>> =
    repository.retrieveInvitation(
      account = account,
      invitationCode = invitationCode
    )

  /**
   * @see SocRecRelationshipsRepository.acceptInvitation
   */
  suspend fun acceptInvitation(
    invitation: Invitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ) = repository.acceptInvitation(
    account = account,
    invitation = invitation,
    protectedCustomerAlias = protectedCustomerAlias,
    trustedContactIdentityKey = trustedContactIdentityKey
  )
}
