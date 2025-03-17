package build.wallet.frost

/**
 * Wrapper class for a base64-encoded sealed response.
 */
data class SealedResponse(
  val value: String,
)

data class UnsealedResponse(
  val value: String,
)
