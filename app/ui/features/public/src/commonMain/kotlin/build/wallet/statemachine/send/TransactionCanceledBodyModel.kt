package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class TransactionCanceledBodyModel(
  override val id: EventTrackerScreenId,
  val onExit: () -> Unit,
) : FormBodyModel(
    id = id,
    onBack = onExit,
    toolbar = null,
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Transaction canceled",
      subline = "For your security, this transaction was canceled because verification wasn’t completed. Start a new transaction to try again.\n\n" +
        "If the amount or address doesn’t match what you entered in the Bitkey app, contact Support."
    ),
    primaryButton = ButtonModel(
      text = "Got it",
      onClick = StandardClick(onExit),
      size = ButtonModel.Size.Footer
    )
  )
