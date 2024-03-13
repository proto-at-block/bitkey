package build.wallet.bitkey.socrec

import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.TAMPERED
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.VERIFIED
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A person that a [ProtectedCustomer] knows and trusts, who they choose to serve as a verification mechanism
 * for Social Recovery.
 */
@Serializable
data class TrustedContact(
  @SerialName("recovery_relationship_id")
  override val recoveryRelationshipId: String,
  @SerialName("trusted_contact_alias")
  override val trustedContactAlias: TrustedContactAlias,
  @SerialName("delegated_decryption_pubkey_certificate")
  @Serializable(with = TrustedContactKeyCertificateAsBase64JsonSerializer::class)
  val keyCertificate: TrustedContactKeyCertificate,
  /**
   * The default auth state is [AWAITING_VERIFY] because the server does not store the auth state,
   * and the app cannot assume that the contact is verified.
   */
  @Transient
  val authenticationState: TrustedContactAuthenticationState = AWAITING_VERIFY,
) : RecoveryContact {
  init {
    require(authenticationState == VERIFIED || authenticationState == TAMPERED || authenticationState == AWAITING_VERIFY) {
      "TrustedContact can only be in the AWAITING_VERIFY, VERIFIED, or TAMPERED state. Found: $authenticationState"
    }
  }

  val identityKey get() = keyCertificate.delegatedDecryptionKey
}
