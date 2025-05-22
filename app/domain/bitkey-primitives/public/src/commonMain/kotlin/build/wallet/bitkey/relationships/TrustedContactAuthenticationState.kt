package build.wallet.bitkey.relationships

/**
 * States of authentication that a Recovery Contact can be in.
 */
enum class TrustedContactAuthenticationState {
  /**
   * The [EndorsedTrustedContact] has not been verified by the Protected Customer.
   */
  AWAITING_VERIFY,

  /**
   * The [UnendorsedTrustedContact] has not been endorsed by the Protected Customer.
   */
  UNAUTHENTICATED,

  /**
   * The [EndorsedTrustedContact] has been endorsed by the Protected Customer. There is a valid endorsement
   * key certificate on the server.
   */
  VERIFIED,

  /**
   * The data required to complete PAKE key confirmation are not available for this [Invitation] or
   * [UnendorsedTrustedContact]. It's not possible to complete key confirmation. The enrollment
   * process will have to be restarted.
   */
  PAKE_DATA_UNAVAILABLE,

  /**
   * The [UnendorsedTrustedContact] has failed PAKE key confirmation. The enrollment process will
   * have to be restarted with a new invitation.
   */
  FAILED,

  /**
   * The [EndorsedTrustedContact]'s endorsement key certificate or their delegate key has been tampered
   * with and are invalid.
   */
  TAMPERED,
}
