package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.form.FormBodyModel

/**
 * Produce [FormBodyModel] mapped from [NfcException].
 *
 * TOOD(BKR-1117): make [segment] and [actionDescription] non-nullable.
 */
fun NfcErrorFormBodyModel(
  exception: NfcException,
  onPrimaryButtonClick: () -> Unit,
  segment: AppSegment?,
  actionDescription: String?,
  onSecondaryButtonClick: () -> Unit,
  eventTrackerScreenId: EventTrackerScreenId?,
  eventTrackerScreenIdContext: NfcEventTrackerScreenIdContext?,
): FormBodyModel {
  val message = NfcErrorMessage.fromException(exception)

  val secondaryButton =
    when (exception) {
      is NfcException.InauthenticHardware ->
        ButtonDataModel(
          "Contact Support",
          onClick = onSecondaryButtonClick
        )
      else -> null
    }

  return ErrorFormBodyModelWithOptionalErrorData(
    title = message.title,
    subline = message.description,
    primaryButton = ButtonDataModel("OK", onClick = onPrimaryButtonClick),
    secondaryButton = secondaryButton,
    errorData = if (segment != null && actionDescription != null) {
      ErrorData(
        segment = segment,
        cause = exception,
        actionDescription = actionDescription
      )
    } else {
      null
    },
    eventTrackerScreenId = eventTrackerScreenId,
    eventTrackerContext = eventTrackerScreenIdContext,
    secondaryButtonIcon = Icon.SmallIconArrowUpRight
  )
}
