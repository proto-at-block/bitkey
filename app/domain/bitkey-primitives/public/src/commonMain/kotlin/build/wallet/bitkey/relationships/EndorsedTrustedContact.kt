package build.wallet.bitkey.relationships

import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.AWAITING_VERIFY
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.TAMPERED
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED

/**
 * A person that a [ProtectedCustomer] knows and trusts, who they choose to serve as a verification mechanism
 * for Social Recovery.
 */
data class EndorsedTrustedContact(
  override val id: RelationshipId,
  override val trustedContactAlias: TrustedContactAlias,
  override val roles: Set<TrustedContactRole>,
  val keyCertificate: TrustedContactKeyCertificate,
  val authenticationState: TrustedContactAuthenticationState = AWAITING_VERIFY,
) : TrustedContact {
  init {
    require(
      authenticationState == VERIFIED ||
        authenticationState == TAMPERED ||
        authenticationState == AWAITING_VERIFY
    ) {
      "TrustedContact has an invalid authentication state: $authenticationState"
    }
  }

  val identityKey get() = keyCertificate.delegatedDecryptionKey

  /** An authenticated certificate that still needs a real hardware endorsement. */
  val needsHwVerification: Boolean
    get() = authenticationState == AWAITING_VERIFY &&
      keyCertificate.appAuthGlobalKeyHwSignature.isW3OnboardingPlaceholder
}

/**
 * We sync all relationships, including inheritance beneficiaries which become [EndorsedTrustedContact]s.
 * This filters that to only be the [EndorsedTrustedContact]s that are relevant for Social Recovery
 * purposes.
 */
fun List<EndorsedTrustedContact>.socialRecoveryTrustedContacts(): List<EndorsedTrustedContact> {
  return this.filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) }
}
