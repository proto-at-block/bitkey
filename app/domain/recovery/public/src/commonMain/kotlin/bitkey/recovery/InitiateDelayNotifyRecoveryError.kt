package bitkey.recovery

sealed class InitiateDelayNotifyRecoveryError : Error() {
  data class CommsVerificationRequiredError(
    override val cause: Error,
  ) : InitiateDelayNotifyRecoveryError()

  data class RecoveryAlreadyExistsError(
    override val cause: Error,
  ) : InitiateDelayNotifyRecoveryError()

  data class OtherError(
    override val cause: Error,
  ) : InitiateDelayNotifyRecoveryError()
}
