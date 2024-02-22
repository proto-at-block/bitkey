package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.form.FormBodyModel

/** Produce [FormBodyModel] mapped from [NfcException]. */
fun NfcErrorFormBodyModel(
  exception: NfcException,
  onPrimaryButtonClick: () -> Unit,
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

  return ErrorFormBodyModel(
    title = message.title,
    subline = message.description,
    primaryButton = ButtonDataModel("OK", onClick = onPrimaryButtonClick),
    secondaryButton = secondaryButton,
    eventTrackerScreenId = eventTrackerScreenId,
    eventTrackerScreenIdContext = eventTrackerScreenIdContext,
    secondaryButtonIcon = Icon.SmallIconArrowUpRight
  )
}
