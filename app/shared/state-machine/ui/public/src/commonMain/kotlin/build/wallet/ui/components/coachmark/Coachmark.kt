package build.wallet.ui.components.coachmark

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.model.coachmark.NewCoachmarkTreatment
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.painter
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Popover-style coachmark
 * @param model The model to use to display the coachmark.
 * @param offset The offset to apply to the coachmark.
 */
@Composable
fun Coachmark(
  model: CoachmarkModel,
  offset: Offset,
) {
  val density = LocalDensity.current

  Column(
    modifier = Modifier
      .padding(start = 16.dp, end = 16.dp)
      .fillMaxWidth()
      .offset(with(density) { offset.x.toDp() }, with(density) { offset.y.toDp() })
  ) {
    // Top arrow if needed
    if (model.arrowPosition.vertical == CoachmarkModel.ArrowPosition.Vertical.Top) {
      Row(
        modifier = Modifier
          .offset(y = 1.dp)
          .height(12.dp)
          .align(
            when (model.arrowPosition.horizontal) {
              CoachmarkModel.ArrowPosition.Horizontal.Leading -> Alignment.Start
              CoachmarkModel.ArrowPosition.Horizontal.Centered -> Alignment.CenterHorizontally
              CoachmarkModel.ArrowPosition.Horizontal.Trailing -> Alignment.End
            }
          ).padding(start = 16.dp, end = 16.dp)
      ) {
        IconImage(
          model = IconModel(
            icon = Icon.CalloutArrow,
            iconSize = IconSize.Small
          ),
          color = WalletTheme.colors.coachmarkBackground
        )
      }
    }

    // Coachmark body
    Row(
      modifier = Modifier
        .fillMaxWidth()
    ) {
      Card(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = WalletTheme.colors.coachmarkBackground,
        modifier = Modifier
          .shadow(elevation = 8.dp, RoundedCornerShape(12.dp), spotColor = Color(0x0A000000), ambientColor = Color(0x0A000000))
          .fillMaxWidth()
      ) {
        Column(
          horizontalAlignment = Alignment.Start,
          modifier = Modifier.padding(16.dp)
        ) {
          // Image
          model.image?.let {
            Image(
              painter = it.painter(),
              contentDescription = null
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Row {
            // New label
            NewCoachmark(NewCoachmarkTreatment.Dark)

            Spacer(modifier = Modifier.weight(1f))

            // Close button
            IconButton(
              iconModel = IconModel(
                icon = Icon.SmallIconXFilled,
                iconSize = IconSize.Small
              ),
              color = Color.Gray,
              onClick = {
                model.dismiss()
              }
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Title
          Label(
            text = model.title,
            type = LabelType.Title2,
            treatment = LabelTreatment.Unspecified,
            color = Color.White
          )
          // Description
          Label(
            text = model.description,
            type = LabelType.Body3Regular,
            treatment = LabelTreatment.Unspecified,
            color = Color.White
          )
          // Button
          model.button?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Button(model = it)
          }
        }
      }
    }
    // Bottom arrow if needed
    if (model.arrowPosition.vertical == CoachmarkModel.ArrowPosition.Vertical.Bottom) {
      Row(
        modifier = Modifier
          .offset(y = (-1).dp)
          .height(12.dp)
          .align(
            when (model.arrowPosition.horizontal) {
              CoachmarkModel.ArrowPosition.Horizontal.Leading -> Alignment.Start
              CoachmarkModel.ArrowPosition.Horizontal.Centered -> Alignment.CenterHorizontally
              CoachmarkModel.ArrowPosition.Horizontal.Trailing -> Alignment.End
            }
          ).padding(start = 16.dp, end = 16.dp)
      ) {
        IconImage(
          model = IconModel(
            icon = Icon.CalloutArrow,
            iconSize = IconSize.Small
          ),
          modifier = Modifier.rotate(degrees = 180f),
          color = WalletTheme.colors.coachmarkBackground
        )
      }
    }
  }
}

@Preview
@Composable
internal fun CoachmarkPreviews() {
  PreviewWalletTheme {
    Coachmark(
      model = CoachmarkModel(
        identifier = CoachmarkIdentifier.MultipleFingerprintsCoachmark,
        title = "Multiple fingerprints",
        description = "Now you can add more fingerprints to your Bitkey device.",
        arrowPosition = CoachmarkModel.ArrowPosition(
          vertical = CoachmarkModel.ArrowPosition.Vertical.Top,
          horizontal = CoachmarkModel.ArrowPosition.Horizontal.Trailing
        ),
        button = ButtonModel(
          text = "Add fingerprints",
          size = ButtonModel.Size.Footer,
          onClick = StandardClick {}
        ),
        image = null,
        dismiss = {}
      ),
      offset = Offset(0f, 0f)
    )
  }
}
