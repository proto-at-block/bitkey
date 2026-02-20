package build.wallet.ui.app.paparazzi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.ui.Modifier
import build.wallet.kotest.paparazzi.PaparazziExtension
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.ui.components.sheet.Sheet
import build.wallet.ui.theme.WalletTheme

/**
 * Captures UI snapshot of a [Sheet] for given model.
 *
 * Renders actual sheet layout with [model]'s UI content.
 * Uses blank background behind the sheet.
 */
fun PaparazziExtension.snapshotSheet(model: SheetModel) {
  snapshot {
    Column(
      Modifier
        .fillMaxSize()
        .background(WalletTheme.colors.background)
    ) {
      Sheet(
        model = model,
        sheetState = run {
          SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { 0.0f },
            velocityThreshold = { 0.0f },
            initialValue = Expanded
          )
        }
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
