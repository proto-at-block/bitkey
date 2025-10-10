package build.wallet.chaincode.delegation

sealed class ChaincodeDelegationError(
  override val cause: Throwable?,
  override val message: String?,
) : Error(message, cause) {
  class KeyDerivation(
    cause: Throwable?,
    message: String?,
  ) : ChaincodeDelegationError(cause, message)

  class KeyMismatch(cause: Throwable?, message: String?) : ChaincodeDelegationError(cause, message)

  class TweakComputation(cause: Throwable?, message: String?) : ChaincodeDelegationError(cause, message)

  class PublicKeyExtraction(cause: Throwable?, message: String?) : ChaincodeDelegationError(cause, message)

  class UnknownKey(cause: Throwable?, message: String?) : ChaincodeDelegationError(cause, message)

  class Unknown(cause: Throwable?, message: String?) : ChaincodeDelegationError(cause, message)
}
