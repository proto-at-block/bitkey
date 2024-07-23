package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.nfc.NfcManagerError
import build.wallet.statemachine.core.form.FormBodyModel

/** Produce [FormBodyModel] mapped from [NfcManagerError]. */
fun NfcErrorFormBodyModel(
  error: NfcManagerError,
  onPrimaryButtonClick: () -> Unit,
  eventTrackerScreenId: EventTrackerScreenId?,
  eventTrackerScreenIdContext: NfcEventTrackerScreenIdContext?,
): FormBodyModel {
  val message = NfcErrorMessage.fromError(error)
  return ErrorFormBodyModel(
    title = message.title,
    subline = message.description,
    primaryButton = ButtonDataModel("OK", onClick = onPrimaryButtonClick),
    eventTrackerScreenId = eventTrackerScreenId,
    eventTrackerContext = eventTrackerScreenIdContext
  )
}
