package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import bitkey.privilegedactions.PrivilegedActionError
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.form.FormBodyModel

sealed class FingerprintResetErrorBodyModel {
  abstract fun toFormBodyModel(
    onRetry: () -> Unit,
    onCancel: () -> Unit,
  ): FormBodyModel

  data class CreatePrivilegedActionError(
    val error: PrivilegedActionError,
  ) : FingerprintResetErrorBodyModel() {
    override fun toFormBodyModel(
      onRetry: () -> Unit,
      onCancel: () -> Unit,
    ): FormBodyModel =
      createErrorFormBodyModel(
        title = "Error Starting Reset",
        message = "We couldn't start the fingerprint reset process. Please try again.",
        primaryButtonText = "Retry",
        onPrimaryButtonClick = onRetry,
        secondaryButtonText = "Cancel",
        onSecondaryButtonClick = onCancel,
        actionDescription = "Starting fingerprint reset",
        cause = PrivilegedActionThrowable(error),
        eventTrackerScreenId = FingerprintResetEventTrackerScreenId.ERROR_STARTING_RESET
      )
  }

  data class CompletePrivilegedActionError(
    val error: PrivilegedActionError,
  ) : FingerprintResetErrorBodyModel() {
    override fun toFormBodyModel(
      onRetry: () -> Unit,
      onCancel: () -> Unit,
    ): FormBodyModel =
      createErrorFormBodyModel(
        title = "Error Completing Reset",
        message = "We couldn't finalize the fingerprint reset process. Please try again.",
        primaryButtonText = "Retry",
        onPrimaryButtonClick = onRetry,
        secondaryButtonText = "Cancel",
        onSecondaryButtonClick = onCancel,
        actionDescription = "Completing fingerprint reset",
        cause = PrivilegedActionThrowable(error),
        eventTrackerScreenId = FingerprintResetEventTrackerScreenId.ERROR_FINALIZING_RESET
      )
  }

  data class NfcError(
    val cause: Throwable?,
  ) : FingerprintResetErrorBodyModel() {
    override fun toFormBodyModel(
      onRetry: () -> Unit,
      onCancel: () -> Unit,
    ): FormBodyModel =
      createErrorFormBodyModel(
        title = "NFC Error",
        message = "There was an issue communicating with your hardware. Please try again.",
        primaryButtonText = "Retry",
        onPrimaryButtonClick = onRetry,
        secondaryButtonText = "Cancel",
        onSecondaryButtonClick = onCancel,
        actionDescription = "NFC communication during fingerprint reset",
        cause = cause ?: RuntimeException("NFC communication failed"),
        eventTrackerScreenId = FingerprintResetEventTrackerScreenId.ERROR_NFC_OPERATION_FAILED
      )
  }

  data class GenericError(
    val title: String,
    val message: String,
    val cause: Throwable?,
    val eventTrackerScreenId: EventTrackerScreenId,
    val primaryButtonText: String = "Retry",
    val secondaryButtonText: String = "Cancel",
  ) : FingerprintResetErrorBodyModel() {
    override fun toFormBodyModel(
      onRetry: () -> Unit,
      onCancel: () -> Unit,
    ): FormBodyModel =
      createErrorFormBodyModel(
        title = title,
        message = message,
        primaryButtonText = primaryButtonText,
        onPrimaryButtonClick = onRetry,
        secondaryButtonText = secondaryButtonText,
        onSecondaryButtonClick = onCancel,
        actionDescription = "Generic error in flow",
        cause = cause ?: RuntimeException(message),
        eventTrackerScreenId = eventTrackerScreenId
      )
  }

  companion object {
    private fun createErrorFormBodyModel(
      title: String,
      message: String,
      primaryButtonText: String,
      onPrimaryButtonClick: () -> Unit,
      secondaryButtonText: String,
      onSecondaryButtonClick: () -> Unit,
      actionDescription: String,
      cause: Throwable,
      eventTrackerScreenId: EventTrackerScreenId,
    ): FormBodyModel =
      ErrorFormBodyModel(
        title = title,
        subline = message,
        primaryButton = ButtonDataModel(
          text = primaryButtonText,
          onClick = onPrimaryButtonClick
        ),
        secondaryButton = ButtonDataModel(
          text = secondaryButtonText,
          onClick = onSecondaryButtonClick
        ),
        errorData = ErrorData(
          segment = FingerprintResetSegment,
          actionDescription = actionDescription,
          cause = cause
        ),
        eventTrackerScreenId = eventTrackerScreenId
      )
  }
}

internal class PrivilegedActionThrowable(
  paError: PrivilegedActionError,
) : Throwable("PrivilegedActionError: $paError")
