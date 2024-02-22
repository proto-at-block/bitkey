package build.wallet.bitkey.socrec

/**
 * States of authentication that a Trusted Contact can be in.
 */
enum class TrustedContactAuthenticationState {
  /**
   * The Trusted Contact has not been endorsed by the Protected Customer.
   */
  UNENDORSED,

  /**
   * The Trusted Contact has been endorsed by the Protected Customer. There is a valid endorsement
   * key certificate on the server.
   */
  ENDORSED,

  /**
   * The data required to complete PAKE key confirmation are not available. It's not possible to
   * complete key confirmation. The enrollment process will have to be restarted.
   */
  PAKE_DATA_UNAVAILABLE,

  /**
   * The Trusted Contact has failed PAKE key confirmation. The enrollment process will have to
   * be restarted with a new invitation.
   */
  FAILED,

  /**
   * The Trusted Contact's endorsement key certificate or their delegate key has been tampered
   * with and are invalid.
   */
  TAMPERED,
}
