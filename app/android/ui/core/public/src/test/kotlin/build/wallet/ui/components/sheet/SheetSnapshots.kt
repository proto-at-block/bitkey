package build.wallet.ui.components.sheet

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue.Expanded
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.StandardClick
import io.kotest.core.spec.style.FunSpec

class SheetSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("sheet") {
    paparazzi.snapshot {
      Column(Modifier.fillMaxSize()) {
        Sheet(
          onClosed = {}, sheetState = sheetState()
        ) {
          FakeSheetContent()
        }
      }
    }
  }
})

@Composable
private fun FakeSheetContent() {
  Column(
    modifier = Modifier
      .border(width = 1.dp, color = Red)
      .fillMaxWidth(),
    horizontalAlignment = CenterHorizontally
  ) {
    Label("Hello")
    Button(text = "Click", onClick = StandardClick {})
  }
}

@Composable
private fun sheetState() =
  SheetState(
    initialValue = Expanded,
    density = LocalDensity.current,
    skipPartiallyExpanded = true
  )
