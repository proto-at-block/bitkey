package build.wallet.nfc

sealed class NfcException : Error() {
  sealed class CanBeRetried : NfcException() {
    /**
     * Indicates that the connection to the tag was lost.
     * Expected error that can occur when the Bitkey device moves out of NFC range during
     * the transaction. Can be retried.
     */
    class TagLost(
      override val message: String? = null,
      override val cause: Throwable? = null,
    ) : CanBeRetried()

    /**
     * Indicates that there was a failure in sending / receiving data from the tag.
     * Expected error that can occur when the Bitkey device moves out of NFC range during
     * the transaction. Can be retried.
     */
    class TransceiveFailure(
      override val message: String? = null,
      override val cause: Throwable? = null,
    ) : CanBeRetried()
  }

  /** Indicates that the NFC connection timed out */
  class Timeout(
    override val message: String? = null,
    override val cause: Throwable? = null,
  ) : NfcException()

  /** Indicates that there was a issue with the request / response of a command */
  class CommandError(
    override val message: String? = null,
    override val cause: Throwable? = null,
  ) : NfcException()

  /** Indicates that the command required the device to first be unlocked and it wasn't */
  class CommandErrorUnauthenticated : NfcException()

  class InauthenticHardware(
    override val message: String? = null,
    override val cause: Throwable? = null,
  ) : NfcException()

  /** Indicates that a transaction was attempted while another was in progress */
  class TransactionInProgress(
    override val message: String? = null,
    override val cause: Throwable? = null,
  ) : NfcException()

  /** Catch-all for unknown or unexpected errors */
  class UnknownError(
    override val message: String? = null,
    override val cause: Throwable? = null,
  ) : NfcException()

  @Suppress("unused")
  sealed class IOSOnly : NfcException() {
    /** Indicates that the data could not be translated into a [NFCISO7816APDU] package. */
    class InvalidAPDU : IOSOnly()

    /** Indicates that the session was explicitly canceled by the user. */
    class UserCancellation(
      override val message: String? = null,
      override val cause: Throwable? = null,
    ) : IOSOnly()

    /**
     * Indicates that the session could not be created or was later invalidated
     * for a generic reason.
     */
    class NoSession(
      override val message: String? = null,
      override val cause: Throwable? = null,
    ) : IOSOnly()

    /** Indicates that the device does not support tag reading. */
    class NotAvailable : IOSOnly()
  }
}

fun Throwable.asNfcException(): NfcException {
  return this as? NfcException ?: NfcException.UnknownError(
    message = message ?: "Cause unknown",
    cause = cause
  )
}
