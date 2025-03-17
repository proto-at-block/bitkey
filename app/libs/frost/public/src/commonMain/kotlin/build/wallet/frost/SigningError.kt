package build.wallet.frost

sealed class SigningError(
  override val cause: Throwable?,
  override val message: String?,
) : Error(message, cause) {
  class InvalidPsbt(cause: Throwable?, message: String?) : SigningError(cause, message)

  class UnableToRetrieveSighash(cause: Throwable?, message: String?) : SigningError(cause, message)

  class InvalidCounterpartyCommitments(cause: Throwable?, message: String?) : SigningError(cause, message)

  class NonceAlreadyUsed(cause: Throwable?, message: String?) : SigningError(cause, message)

  class CommitmentMismatch(cause: Throwable?, message: String?) : SigningError(cause, message)

  class MissingCounterpartyNonces(cause: Throwable?, message: String?) : SigningError(cause, message)

  class UnableToFinalizePsbt(cause: Throwable?, message: String?) : SigningError(cause, message)
}
