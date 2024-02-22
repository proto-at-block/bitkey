package build.wallet.bitkey.socrec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A person who has accepted an invitation from the [ProtectedCustomer], but the customer has not
 * yet confirmed their identity via PAKE authentication.
 */
@Serializable
data class UnendorsedTrustedContact(
  @SerialName("recovery_relationship_id")
  override val recoveryRelationshipId: String,
  @SerialName("trusted_contact_alias")
  override val trustedContactAlias: TrustedContactAlias,
  @SerialName("trusted_contact_identity_pubkey")
  val identityKey: TrustedContactIdentityKey,
  @SerialName("trusted_contact_identity_pubkey_mac")
  val identityPublicKeyMac: String,
  @SerialName("trusted_contact_enrollment_pubkey")
  val enrollmentKey: TrustedContactEnrollmentKey,
  @SerialName("enrollment_key_confirmation")
  val enrollmentKeyConfirmation: String,
  @Transient
  val authenticationState: TrustedContactAuthenticationState =
    TrustedContactAuthenticationState.UNENDORSED,
) : RecoveryContact
