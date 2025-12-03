package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.button.ButtonModel

data class HardwareConfirmationCanceledScreenModel(
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_CANCELED,
    onBack = onBack,
    toolbar = null,
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.IconContent(
          // TODO Replace with new icon
          icon = Icon.BitkeyDevice3D
        ),
        title = "Transaction canceled",
        body = LabelModel.StringModel(
          "Make sure you’ve also canceled the transaction on your Bitkey."
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Done",
      leadingIcon = null,
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      size = ButtonModel.Size.Footer,
      onClick = onBack
    )
  )
