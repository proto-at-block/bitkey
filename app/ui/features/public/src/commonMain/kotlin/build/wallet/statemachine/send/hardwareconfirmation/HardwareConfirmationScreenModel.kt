package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.button.ButtonModel

data class HardwareConfirmationScreenModel(
  override val onBack: () -> Unit,
  val onSend: () -> Unit,
  val onLearnMore: () -> Unit,
) : FormBodyModel(
    id = SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION,
    onBack = onBack,
    toolbar = null,
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.IconContent(
          // TODO Replace with new icon
          icon = Icon.BitkeyDevice3D
        ),
        title = "Are the details on your Bitkey device correct?",
        body = LabelModel.LinkSubstringModel.from(
          substringToOnClick = mapOf(
            "Learn more" to onLearnMore
          ),
          string = "Confirm or cancel the signing of the transaction on your Bitkey.\n\nLearn more",
          underline = true,
          bold = true,
          color = LabelModel.Color.ON60
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Yes, send",
      requiresBitkeyInteraction = true,
      treatment = ButtonModel.Treatment.BitkeyInteraction,
      size = ButtonModel.Size.Footer,
      onClick = onSend
    ),
    secondaryButton = ButtonModel(
      text = "No, cancel transaction",
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = onBack
    )
  )
