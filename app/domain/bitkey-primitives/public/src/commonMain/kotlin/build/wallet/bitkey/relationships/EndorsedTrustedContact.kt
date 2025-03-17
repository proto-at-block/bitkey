package build.wallet.bitkey.relationships

import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.TAMPERED
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED

/**
 * A person that a [ProtectedCustomer] knows and trusts, who they choose to serve as a verification mechanism
 * for Social Recovery.
 */
data class EndorsedTrustedContact(
  override val relationshipId: String,
  override val trustedContactAlias: TrustedContactAlias,
  override val roles: Set<TrustedContactRole>,
  val keyCertificate: TrustedContactKeyCertificate,
  val authenticationState: TrustedContactAuthenticationState = AWAITING_VERIFY,
) : TrustedContact {
  init {
    require(authenticationState == VERIFIED || authenticationState == TAMPERED || authenticationState == AWAITING_VERIFY) {
      "TrustedContact can only be in the AWAITING_VERIFY, VERIFIED, or TAMPERED state. Found: $authenticationState"
    }
  }

  val identityKey get() = keyCertificate.delegatedDecryptionKey
}

/**
 * We sync all relationships, including inheritance beneficiaries which become [EndorsedTrustedContact]s.
 * This filters that to only be the [EndorsedTrustedContact]s that are relevant for Social Recovery
 * purposes.
 */
fun List<EndorsedTrustedContact>.socialRecoveryTrustedContacts(): List<EndorsedTrustedContact> {
  return this.filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) }
}
