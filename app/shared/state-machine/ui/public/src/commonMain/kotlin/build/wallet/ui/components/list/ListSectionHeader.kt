package build.wallet.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupModel.HeaderTreatment.PRIMARY
import build.wallet.ui.model.list.ListGroupModel.HeaderTreatment.SECONDARY
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ListSectionHeader(
  modifier: Modifier = Modifier,
  title: String,
  treatment: ListGroupModel.HeaderTreatment,
) {
  Box(modifier = modifier.fillMaxWidth()) {
    Label(
      modifier =
        Modifier.padding(
          top = 8.dp
        ),
      text = title,
      type = when (treatment) {
        SECONDARY -> LabelType.Title3
        PRIMARY -> LabelType.Title2
      },
      treatment = when (treatment) {
        SECONDARY -> Secondary
        PRIMARY -> Primary
      }
    )
  }
}

@Composable
@Preview
internal fun ListSectionHeaderPreview() {
  PreviewWalletTheme {
    ListHeader(title = "Pending")
  }
}
