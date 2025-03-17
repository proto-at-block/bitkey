package build.wallet.ui.components.button

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.icon.IconSize.Small

@Preview
@Composable
private fun RowOfTwoButtonsContainerPreview() {
  RowOfButtons(
    buttonContents =
      ButtonContentsList(
        listOf(
          {
            Button(
              text = "First",
              treatment = Primary,
              leadingIcon = Icon.SmallIconBitkey,
              size = ButtonModel.Size.Regular,
              onClick = StandardClick {}
            )
          },
          {
            Button(
              text = "Second",
              treatment = Secondary,
              size = ButtonModel.Size.Regular,
              onClick = StandardClick {}
            )
          }
        )
      ),
    interButtonSpacing = 16.dp
  )
}

@Preview
@Composable
private fun RowOfThreeButtonsContainerPreview() {
  val sendModel =
    IconButtonModel(
      iconModel =
        IconModel(
          LocalImage(Icon.SmallIconArrowUp),
          iconSize = Small,
          iconBackgroundType =
            IconBackgroundType.Circle(
              circleSize = Avatar
            ),
          text = "Send"
        ),
      onClick = StandardClick {}
    )
  RowOfButtons(
    buttonContents =
      ButtonContentsList(
        listOf(
          {
            IconButton(model = sendModel)
          },
          {
            IconButton(model = sendModel)
          },
          {
            IconButton(model = sendModel)
          }
        )
      ),
    interButtonSpacing = 40.dp
  )
}
