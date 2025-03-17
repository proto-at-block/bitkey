package build.wallet.ui.app.core.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType.*

@Composable
fun UpsellContainer(
  model: FormMainContentModel.Upsell,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .background(WalletTheme.colors.background)
      .clip(RoundedCornerShape(20.dp))
      .border(
        BorderStroke(2.dp, WalletTheme.colors.foreground10),
        RoundedCornerShape(20.dp)
      )
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    IconImage(
      model = model.iconModel,
      color = WalletTheme.colors.calloutDefaultTrailingIconBackground
    )
    Label(
      model = StringModel(model.title),
      type = Title2,
      treatment = LabelTreatment.Primary
    )
    Label(
      model = StringModel(model.body),
      type = Body3Regular,
      alignment = TextAlign.Center,
      treatment = LabelTreatment.Secondary
    )
    Row(
      horizontalArrangement = Arrangement.SpaceEvenly,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp)
        .padding(horizontal = 8.dp)
    ) {
      Button(
        model = model.primaryButton,
        modifier = Modifier.weight(0.5f)
      )
      Spacer(Modifier.width(16.dp))
      Button(
        model = model.secondaryButton,
        modifier = Modifier.weight(0.5f)
      )
    }
  }
}
