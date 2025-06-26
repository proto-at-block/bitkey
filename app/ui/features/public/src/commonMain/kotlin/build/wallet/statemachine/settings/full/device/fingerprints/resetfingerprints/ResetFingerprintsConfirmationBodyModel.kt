package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

data class ResetFingerprintsConfirmationBodyModel(
  val onClose: () -> Unit,
  val onConfirmReset: () -> Unit,
) : FormBodyModel(
    id = ResetFingerprintsEventTrackerScreenId.CONFIRM_RESET_FINGERPRINTS,
    onBack = onClose,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onClose)
    ),
    header = FormHeaderModel(
      headline = "Start fingerprint reset",
      subline = "If you're unable to unlock your Bitkey device, you can reset your fingerprints."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          header = "How it works",
          headerTreatment = ListGroupModel.HeaderTreatment.PRIMARY,
          style = ListGroupStyle.NONE,
          items = confirmationSteps.map { (number, title, secondaryText) ->
            ListItemModel(
              leadingAccessory = ListItemAccessory.CircularCharacterAccessory(number),
              leadingAccessoryAlignment = ListItemAccessoryAlignment.TOP,
              title = title,
              secondaryText = secondaryText,
              treatment = ListItemTreatment.PRIMARY
            )
          }.toImmutableList()
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Start fingerprint reset",
      onClick = StandardClick(onConfirmReset),
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary
    )
  )

private val confirmationSteps = listOf(
  Triple(
    '1',
    "7-day security period",
    "During the security period, you'll receive notifications that allow you to cancel the process."
  ),
  Triple(
    '2',
    "Cancel anytime",
    "You can stop this process at any point and continue using your existing fingerprints."
  ),
  Triple(
    '3',
    "Set up new fingerprints",
    "After 7 days, you’ll be able to save a new set of fingerprints."
  )
)
