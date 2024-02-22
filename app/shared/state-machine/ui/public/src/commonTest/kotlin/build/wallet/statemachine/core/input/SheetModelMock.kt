package build.wallet.statemachine.core.input

import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.RenderContext.Sheet

fun SheetModelMock(onBack: () -> Unit) =
  SheetModel(
    onClosed = onBack,
    body =
      ErrorFormBodyModel(
        title = "",
        primaryButton = ButtonDataModel("", onClick = {}),
        renderContext = Sheet,
        eventTrackerScreenId = null
      )
  )
