package build.wallet.statemachine.moneyhome

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.ArrowDown
import build.wallet.statemachine.core.Icon.ArrowUp
import build.wallet.statemachine.core.Icon.Minus
import build.wallet.statemachine.core.Icon.Plus
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

/**
 * Possible button configurations on Money Home
 */
sealed interface MoneyHomeButtonsModel {
  /**
   * A set of 4 buttons - Send, Receive, Buy, Sell - displayed as fixed size circular icon buttons
   */
  data class MoneyMovementButtonsModel(
    private val addButton: Button,
    private val sellButton: Button,
    private val sendButton: Button,
    private val receiveButton: Button,
  ) : MoneyHomeButtonsModel {
    val buttons: List<IconButtonModel> = listOf(
      MoneyMovementIconModel("Buy", Plus, addButton),
      MoneyMovementIconModel("Sell", Minus, sellButton),
      MoneyMovementIconModel("Send", ArrowUp, sendButton),
      MoneyMovementIconModel("Receive", ArrowDown, receiveButton)
    )

    data class Button(
      val enabled: Boolean,
      val onClick: () -> Unit,
    )
  }

  /**
   * A single button that fills the width of the container.
   */
  data class SingleButtonModel(
    val button: ButtonModel,
  ) : MoneyHomeButtonsModel {
    constructor(onSetUpBitkeyDevice: () -> Unit) : this(
      button =
        ButtonModel(
          text = "Set up Bitkey Device",
          treatment = ButtonModel.Treatment.Secondary,
          size = ButtonModel.Size.Footer,
          onClick = StandardClick(onSetUpBitkeyDevice)
        )
    )
  }
}

fun MoneyMovementIconModel(
  text: String,
  icon: Icon,
  button: MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button,
) = IconButtonModel(
  enabled = button.enabled,
  testTag = "money-home-action-$text",
  iconModel =
    IconModel(
      IconImage.LocalImage(icon),
      iconSize = IconSize.Regular,
      iconBackgroundType = IconBackgroundType.Circle(
        circleSize = IconSize.AvatarLarge,
        color = IconBackgroundType.Circle.CircleColor.Secondary
      ),
      text = text,
      iconTint = if (button.enabled) null else IconTint.On30
    ),
  onClick = StandardClick { button.onClick() }
)
