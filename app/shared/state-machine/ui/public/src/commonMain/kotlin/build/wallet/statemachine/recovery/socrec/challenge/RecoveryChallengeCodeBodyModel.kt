package build.wallet.statemachine.recovery.socrec.challenge

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarModel

fun RecoveryChallengeCodeBodyModel(
  recoveryChallengeCode: String,
  onBack: () -> Unit,
  onDone: () -> Unit,
) = FormBodyModel(
  id = SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TC_VERIFICATION_CODE,
  toolbar = ToolbarModel(),
  header =
    FormHeaderModel(
      headline = "Share recovery code",
      subline = "Call your Trusted Contact and have them enter this code within their app."
    ),
  mainContentList =
    immutableListOf(
      FormMainContentModel.ListGroup(
        ListGroupModel(
          items =
            immutableListOf(
              ListItemModel(
                title = recoveryChallengeCode,
                treatment = ListItemTreatment.PRIMARY
              )
            ),
          style = ListGroupStyle.NONE
        )
      )
    ),
  onBack = onBack,
  primaryButton =
    ButtonModel(
      text = "Done",
      size = ButtonModel.Size.Footer,
      onClick = Click.standardClick { onDone() }
    )
)