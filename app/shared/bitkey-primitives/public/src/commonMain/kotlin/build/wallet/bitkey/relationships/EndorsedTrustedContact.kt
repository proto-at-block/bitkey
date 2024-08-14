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
