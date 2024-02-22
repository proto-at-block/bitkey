package build.wallet.statemachine.moneyhome

import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.LargeIconAdd
import build.wallet.statemachine.core.Icon.LargeIconReceive
import build.wallet.statemachine.core.Icon.LargeIconSend
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.icon.IconTint
import kotlinx.collections.immutable.ImmutableList

/**
 * Possible button configurations on Money Home
 */
sealed interface MoneyHomeButtonsModel {
  /**
   * A set of 3 buttons - Send, Receive, Add - displayed as fixed size circular icon buttons
   */
  data class MoneyMovementButtonsModel(
    private val sendButton: Button,
    private val receiveButton: Button,
    private val addButton: Button,
  ) : MoneyHomeButtonsModel {
    val buttons: ImmutableList<IconButtonModel> =
      immutableListOf(
        MoneyMovementIconModel("Send", LargeIconSend, sendButton),
        MoneyMovementIconModel("Receive", LargeIconReceive, receiveButton),
        MoneyMovementIconModel("Add", LargeIconAdd, addButton)
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
          onClick = Click.standardClick { onSetUpBitkeyDevice() }
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
  iconModel =
    IconModel(
      IconImage.LocalImage(icon),
      iconSize = Avatar,
      iconBackgroundType = IconBackgroundType.Circle(circleSize = Avatar),
      text = text,
      iconTint = if (button.enabled) null else IconTint.On30
    ),
  onClick = Click.standardClick { button.onClick() }
)
