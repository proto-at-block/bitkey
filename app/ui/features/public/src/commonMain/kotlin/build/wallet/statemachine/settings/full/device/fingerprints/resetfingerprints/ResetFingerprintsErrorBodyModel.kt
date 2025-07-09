package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import bitkey.privilegedactions.PrivilegedActionError
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.form.FormBodyModel

sealed class ResetFingerprintsErrorBodyModel {
  abstract fun toFormBodyModel(
    onRetry: () -> Unit,
    onCancel: () -> Unit,
  ): FormBodyModel

  data class CreatePrivilegedActionError(
    val error: PrivilegedActionError,
  ) : ResetFingerprintsErrorBodyModel() {
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
        eventTrackerScreenId = ResetFingerprintsEventTrackerScreenId.ERROR_STARTING_RESET
      )
  }

  data class CompletePrivilegedActionError(
    val error: PrivilegedActionError,
  ) : ResetFingerprintsErrorBodyModel() {
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
        eventTrackerScreenId = ResetFingerprintsEventTrackerScreenId.ERROR_FINALIZING_RESET
      )
  }

  data class GenericError(
    val title: String,
    val message: String,
    val cause: Throwable?,
    val eventTrackerScreenId: EventTrackerScreenId,
    val primaryButtonText: String = "Retry",
    val secondaryButtonText: String = "Cancel",
  ) : ResetFingerprintsErrorBodyModel() {
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
          segment = ResetFingerprintsSegment,
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
