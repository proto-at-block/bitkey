package build.wallet.recovery.socrec

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.relationships.IncomingInvitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
import build.wallet.crypto.PublicKey

/**
 * Encapsulates actions trusted contacts can take that can produce side effects.
 */
open class SocRecTrustedContactActions internal constructor(
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
      relationshipId = customer.relationshipId
    )

  /**
   * @see SocRecRelationshipsRepository.retrieveInvitation
   */
  suspend fun retrieveInvitation(invitationCode: String) =
    repository.retrieveInvitation(
      account = account,
      invitationCode = invitationCode
    )

  /**
   * @see SocRecRelationshipsRepository.acceptInvitation
   */
  suspend fun acceptInvitation(
    invitation: IncomingInvitation,
    protectedCustomerAlias: ProtectedCustomerAlias,
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    inviteCode: String,
  ) = repository.acceptInvitation(
    account = account,
    invitation = invitation,
    protectedCustomerAlias = protectedCustomerAlias,
    delegatedDecryptionKey = delegatedDecryptionKey,
    inviteCode = inviteCode
  )
}
