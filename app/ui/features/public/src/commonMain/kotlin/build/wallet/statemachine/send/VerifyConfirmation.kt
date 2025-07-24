package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
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

data class VerifyConfirmation(
  val onContinue: () -> Unit,
  override val onBack: (() -> Unit),
) : FormBodyModel(
    id = TxVerificationEventTrackerScreenId.VERIFICATION_START,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      headline = "Verify your transaction"
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          header = "How it works",
          headerTreatment = ListGroupModel.HeaderTreatment.PRIMARY,
          style = ListGroupStyle.NONE,
          items = immutableListOf(
            ListItemModel(
              leadingAccessory = ListItemAccessory.CircularCharacterAccessory('1'),
              leadingAccessoryAlignment = ListItemAccessoryAlignment.TOP,
              title = "Send verification email",
              secondaryText = "We’ll email you a link to Bitkey’s secure verification site.",
              treatment = ListItemTreatment.PRIMARY
            ),
            ListItemModel(
              leadingAccessory = ListItemAccessory.CircularCharacterAccessory('2'),
              leadingAccessoryAlignment = ListItemAccessoryAlignment.TOP,
              title = "Verify transaction",
              secondaryText = "Follow the link to review the amount range and recipient address.",
              treatment = ListItemTreatment.PRIMARY
            ),
            ListItemModel(
              leadingAccessory = ListItemAccessory.CircularCharacterAccessory('3'),
              leadingAccessoryAlignment = ListItemAccessoryAlignment.TOP,
              title = "Return to Bitkey",
              secondaryText = "Once verified, come back to the app to finish your transaction.",
              treatment = ListItemTreatment.PRIMARY
            )
          )
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Send verification email",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onContinue() }
    )
  )
