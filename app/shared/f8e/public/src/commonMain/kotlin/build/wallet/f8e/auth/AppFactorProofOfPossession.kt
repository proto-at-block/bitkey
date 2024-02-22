package build.wallet.f8e.auth

/**
 * Indicates that we have proof that the customer has possession of a the app
 * via the presence of an auth token whose hash is signed by the app's private authentication key.
 * The key can be a freshly generated key by the app (for example during Lost App recovery), or
 * using an active keybox's authentication key.
 *
 * @param appSignedToken f8e access token signed with hardware's authentication private key.
 */
data class AppFactorProofOfPossession(
  val appSignedToken: String,
)
