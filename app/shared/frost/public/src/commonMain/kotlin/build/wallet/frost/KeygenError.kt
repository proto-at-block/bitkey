package build.wallet.frost

sealed class KeygenError(
  override val cause: Throwable?,
  override val message: String?,
) : Error(message, cause) {
  class MissingSharePackage(cause: Throwable?, message: String?) : KeygenError(cause, message)

  class MissingShareAggParams(cause: Throwable?, message: String?) : KeygenError(cause, message)

  class InvalidParticipantIndex(cause: Throwable?, message: String?) : KeygenError(cause, message)

  class InvalidProofOfKnowledge(cause: Throwable?, message: String?) : KeygenError(cause, message)

  class InvalidIntermediateShare(cause: Throwable?, message: String?) : KeygenError(cause, message)

  class InvalidKeyCommitments(cause: Throwable?, message: String?) : KeygenError(cause, message)

  class InvalidParticipants(cause: Throwable?, message: String?) : KeygenError(cause, message)

  class ShareAggregationFailed(cause: Throwable?, message: String?) : KeygenError(cause, message)

  class VerificationShareGenerationFailed(cause: Throwable?, message: String?) : KeygenError(cause, message)
}
