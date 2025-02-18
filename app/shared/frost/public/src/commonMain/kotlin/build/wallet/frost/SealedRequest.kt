package build.wallet.frost

/**
 * Wrapper class for a base64-encoded sealed request.
 */
data class SealedRequest(
  val value: String,
)

data class UnsealedRequest(
  val value: String,
)
