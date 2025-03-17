package build.wallet.bitkey.relationships

import bitkey.serialization.hex.ByteStringAsHexSerializer
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.UNAUTHENTICATED
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import okio.ByteString

/**
 * A person who has accepted an invitation from the [ProtectedCustomer], but the customer has not
 * yet confirmed their identity via PAKE authentication.
 */
@Serializable
data class UnendorsedTrustedContact(
  @SerialName("recovery_relationship_id")
  override val relationshipId: String,
  @SerialName("trusted_contact_alias")
  override val trustedContactAlias: TrustedContactAlias,
  @SerialName("sealed_delegated_decryption_pubkey")
  val sealedDelegatedDecryptionKey: XCiphertext,
  @SerialName("trusted_contact_enrollment_pake_pubkey")
  val enrollmentPakeKey: PublicKey<TrustedContactEnrollmentPakeKey>,
  @SerialName("enrollment_pake_confirmation")
  @Serializable(with = ByteStringAsHexSerializer::class)
  val enrollmentKeyConfirmation: ByteString,
  @Transient
  val authenticationState: TrustedContactAuthenticationState = UNAUTHENTICATED,
  @SerialName("trusted_contact_roles")
  override val roles: Set<TrustedContactRole>,
) : TrustedContact

/**
 * We sync all relationships, including inheritance beneficiaries which start as [UnendorsedTrustedContact]s.
 * This filters that to only be the [UnendorsedTrustedContact]s that are relevant for Social Recovery
 * purposes.
 */
fun List<UnendorsedTrustedContact>.socialRecoveryUnendorsedTrustedContacts(): List<UnendorsedTrustedContact> {
  return this.filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) }
}
