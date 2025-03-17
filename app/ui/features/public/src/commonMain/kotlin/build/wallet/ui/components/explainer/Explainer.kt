package build.wallet.ui.components.explainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Slot-based implementation, meant to be used with [Statement].
 */
@Composable
fun Explainer(
  modifier: Modifier = Modifier,
  statementsContent: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(24.dp)
  ) {
    statementsContent()
  }
}
