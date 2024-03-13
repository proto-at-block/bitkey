package build.wallet.bitkey.socrec

/**
 * States of authentication that a Trusted Contact can be in.
 */
enum class TrustedContactAuthenticationState {
  /**
   * The [TrustedContact] has not been verified by the Protected Customer.
   */
  AWAITING_VERIFY,

  /**
   * The [UnendorsedTrustedContact] has not been endorsed by the Protected Customer.
   */
  UNAUTHENTICATED,

  /**
   * The [TrustedContact] has been endorsed by the Protected Customer. There is a valid endorsement
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
   * The [TrustedContact]'s endorsement key certificate or their delegate key has been tampered
   * with and are invalid.
   */
  TAMPERED,
}
