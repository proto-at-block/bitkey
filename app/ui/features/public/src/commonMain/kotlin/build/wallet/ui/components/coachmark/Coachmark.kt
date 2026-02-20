package build.wallet.ui.components.coachmark

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.coachmark.CoachmarkLabelTreatment
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.CoachmarkLabelModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter

/**
 * Popover-style coachmark
 * @param model The model to use to display the coachmark.
 * @param offset The offset to apply to the coachmark.
 */
@Composable
fun Coachmark(
  modifier: Modifier = Modifier,
  model: CoachmarkModel,
  offset: Offset,
) {
  val density = LocalDensity.current
  val coachmarkShape = RoundedCornerShape(20.dp)
  val arrowAlignment = when (model.arrowPosition.horizontal) {
    CoachmarkModel.ArrowPosition.Horizontal.Leading -> Alignment.Start
    CoachmarkModel.ArrowPosition.Horizontal.Centered -> Alignment.CenterHorizontally
    CoachmarkModel.ArrowPosition.Horizontal.Trailing -> Alignment.End
  }

  Column(
    modifier = modifier
      .padding(horizontal = 16.dp)
      .fillMaxWidth()
      .offset(with(density) { offset.x.toDp() }, with(density) { offset.y.toDp() })
  ) {
    // Top arrow
    if (model.arrowPosition.vertical == CoachmarkModel.ArrowPosition.Vertical.Top) {
      CoachmarkArrow(modifier = Modifier.align(arrowAlignment).offset(y = 1.dp))
    }

    // Coachmark body
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .shadow(
          elevation = 8.dp,
          shape = coachmarkShape,
          spotColor = Color(0x0A000000),
          ambientColor = Color(0x0A000000)
        )
        .background(color = WalletTheme.colors.coachmarkBackground, shape = coachmarkShape)
        .padding(16.dp)
    ) {
      model.image?.let {
        Image(
          painter = it.painter(),
          contentDescription = null
        )
        Spacer(modifier = Modifier.height(8.dp))
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        CoachmarkLabel(
          model = CoachmarkLabelModel.New.copy(treatment = CoachmarkLabelTreatment.Dark)
        )
        IconButton(
          iconModel = IconModel(
            icon = Icon.SmallIconXFilled,
            iconSize = IconSize.Small
          ),
          color = WalletTheme.colors.foreground30,
          onClick = model.dismiss
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Label(
        text = model.title,
        type = LabelType.Title2,
        treatment = LabelTreatment.Unspecified,
        color = Color.White
      )
      Label(
        text = model.description,
        type = LabelType.Body3Regular,
        treatment = LabelTreatment.Unspecified,
        color = Color.White
      )
      model.button?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Button(model = it)
      }
    }

    // Bottom arrow
    if (model.arrowPosition.vertical == CoachmarkModel.ArrowPosition.Vertical.Bottom) {
      CoachmarkArrow(
        modifier = Modifier.align(arrowAlignment).offset(y = (-1).dp),
        rotated = true
      )
    }
  }
}

@Composable
private fun CoachmarkArrow(
  modifier: Modifier = Modifier,
  rotated: Boolean = false,
) {
  IconImage(
    model = IconModel(
      icon = Icon.CalloutArrow,
      iconSize = IconSize.Small
    ),
    modifier = modifier
      .padding(horizontal = 16.dp)
      .height(12.dp)
      .then(if (rotated) Modifier.rotate(180f) else Modifier),
    color = WalletTheme.colors.coachmarkBackground
  )
}
