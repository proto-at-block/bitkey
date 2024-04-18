package build.wallet.bitkey.socrec

import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.TAMPERED
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.VERIFIED

/**
 * A person that a [ProtectedCustomer] knows and trusts, who they choose to serve as a verification mechanism
 * for Social Recovery.
 */
data class EndorsedTrustedContact(
  override val recoveryRelationshipId: String,
  override val trustedContactAlias: TrustedContactAlias,
  val keyCertificate: TrustedContactKeyCertificate,
  val authenticationState: TrustedContactAuthenticationState = AWAITING_VERIFY,
) : RecoveryContact {
  init {
    require(authenticationState == VERIFIED || authenticationState == TAMPERED || authenticationState == AWAITING_VERIFY) {
      "TrustedContact can only be in the AWAITING_VERIFY, VERIFIED, or TAMPERED state. Found: $authenticationState"
    }
  }

  val identityKey get() = keyCertificate.delegatedDecryptionKey
}
