package build.wallet.ui.app.paparazzi

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import build.wallet.kotest.paparazzi.PaparazziExtension
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.ui.components.sheet.Sheet

/**
 * Captures UI snapshot of a [Sheet] for given model.
 *
 * Renders actual sheet layout with [model]'s UI content.
 * Uses blank background behind the sheet.
 */
fun PaparazziExtension.snapshotSheet(model: SheetModel) {
  snapshot {
    Column(Modifier.fillMaxSize()) {
      Sheet(
        model = model,
        sheetState = SheetState(
          initialValue = Expanded,
          density = LocalDensity.current,
          skipPartiallyExpanded = true
        )
      )
    }
  }
}

/**
 * Captures UI snapshot of a [Sheet] for given [FormBodyModel].
 */
fun PaparazziExtension.snapshotSheet(model: FormBodyModel) {
  snapshotSheet(model.asSheetModalScreen(onClosed = {}))
}
