package build.wallet.ui.components.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconArrowUp
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.icon.IconSize.Small

/**
 * [Button]s side by side in a list.
 * @param buttonContents specifies the list of buttons to be shown
 */
@Composable
fun RowOfButtons(
  modifier: Modifier = Modifier,
  buttonContents: ButtonContentsList,
  interButtonSpacing: Dp,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement =
      Arrangement.spacedBy(
        space = interButtonSpacing,
        alignment = Alignment.CenterHorizontally
      )
  ) {
    for (buttonContent in buttonContents.buttonContents) {
      buttonContent()
    }
  }
}

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
              onClick = Click.StandardClick { }
            )
          },
          {
            Button(
              text = "Second",
              treatment = Secondary,
              size = ButtonModel.Size.Regular,
              onClick = Click.StandardClick { }
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
          LocalImage(SmallIconArrowUp),
          iconSize = Small,
          iconBackgroundType =
            IconBackgroundType.Circle(
              circleSize = Avatar
            ),
          text = "Send"
        ),
      onClick = Click.StandardClick {}
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

/**
 * A list of buttonContents that will be displayed in SplitButtons
 */
@Immutable
data class ButtonContentsList(
  val buttonContents: List<@Composable RowScope.() -> Unit>,
)
