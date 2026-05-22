package build.wallet.nfc

import bitkey.account.HardwareType
import build.wallet.fwup.FwupFinishResponseStatus

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

    /**
     * The active NFC session was invalidated mid-transceive. On iOS this typically
     * corresponds to the NFC coil hitting thermal throttling. Surfaced as a distinct
     * type so FWUP can route it to the thermal cooldown screen; other callers treat
     * it as a generic [CanBeRetried] failure.
     */
    class SessionInvalidated(
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

  /** Indicates that the tapped device has already been wiped or has not completed setup. */
  class DeviceAlreadyWipedOrNotSetUp : NfcException()

  /** Indicates unsealing the csek failed, likely due to using the wrong device. */
  class CommandErrorSealCsekResponseUnsealException : NfcException()

  /** Indicates that a file was not found */
  class CommandErrorFileNotFound : NfcException()

  /** Indicates that the current hardware/firmware does not support this command. */
  class FeatureNotSupported : NfcException()

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

  /**
   * The hardware is not paired to the current user's account.
   * For W3 D+N specifically, prefer [HardwareReplacementPendingError].
   */
  class UnpairedHardwareError(
    override val message: String? = null,
    override val cause: Throwable? = null,
  ) : NfcException()

  /**
   * The tapped W3 hardware is the replacement device, but the Delay+Notify waiting period
   * has not yet expired. The new hardware is not yet authorized to act as the paired device.
   */
  class HardwareReplacementPendingError : NfcException()

  /**
   * W3 two-tap flow: User has not yet approved or denied the confirmation on the device.
   * The app should show a screen prompting the user to make a decision on the device.
   */
  class ConfirmationPending : NfcException()

  /**
   * W3 two-tap flow: User explicitly denied the operation on the device.
   * The app should show a denial acknowledgment screen.
   */
  class UserDenied : NfcException()

  /**
   * W3 two-tap flow: The confirmation was not completed on the device.
   * This occurs when the user cancels, the confirmation times out, or the
   * confirmation state is otherwise no longer valid when the app taps again.
   * The app should show an informational screen and return to the start of the flow.
   */
  class ConfirmationNotCompleted : NfcException()

  /**
   * The hardware device does not have a wallet descriptor (keyset). This means
   * [verifyKeysAndBuildDescriptor] was never called — typically because the user
   * did not complete onboarding before restoring from a cloud backup.
   * The app should trigger the descriptor delivery flow and then retry the operation.
   */
  class DescriptorNotLoaded : NfcException()

  /**
   * Multi-MCU FWUP: The previous MCU's firmware update was not applied on the device.
   * Detected when starting a subsequent MCU update and finding the previous MCU's
   * version hasn't changed to the expected target. The user should restart the update.
   */
  class PreviousMcuUpdateNotApplied(
    override val message: String? = null,
  ) : NfcException()

  /**
   * FWUP: The firmware update finish command returned a non-success status.
   * Carries the specific [FwupFinishResponseStatus] so the UI can show
   * contextual error messaging (e.g. signature invalid vs version invalid).
   */
  class FwupFinishError(
    val status: FwupFinishResponseStatus,
    override val message: String? = null,
  ) : NfcException()

  /**
   * The tapped hardware type does not match what was expected for this operation.
   * For example, tapping a W1 device when a W3 was required, or vice versa.
   */
  class WrongHardwareType(
    val expected: HardwareType,
    val actual: HardwareType,
  ) : NfcException() {
    override val message: String
      get() = "Expected $expected hardware but tapped $actual"
  }

  /**
   * The tapped hardware is running firmware that is too old to complete pairing.
   */
  class PairingFirmwareTooOld(
    val minimumVersion: String,
    val currentVersion: String,
  ) : NfcException() {
    override val message: String
      get() = "Cannot pair hardware on firmware $currentVersion; requires $minimumVersion or later"
  }

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
