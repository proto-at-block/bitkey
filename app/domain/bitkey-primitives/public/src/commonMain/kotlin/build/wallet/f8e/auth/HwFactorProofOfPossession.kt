package build.wallet.f8e.auth

/**
 * Indicates that we have proof that the customer has possession of a the HW
 * via the presence of an auth token whose hash is signed by the HW's private authentication key.
 *
 * @param hwSignedToken f8e access token signed with hardware's authentication private key.
 */
data class HwFactorProofOfPossession(val hwSignedToken: String)
