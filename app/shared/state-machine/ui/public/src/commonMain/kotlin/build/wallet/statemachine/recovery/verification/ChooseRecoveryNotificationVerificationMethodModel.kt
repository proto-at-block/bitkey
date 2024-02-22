package build.wallet.statemachine.recovery.verification

import build.wallet.analytics.events.screen.id.ChooseRecoveryNotificationVerificationMethodScreenId.CHOOSE_RECOVERY_NOTIFICATION_VERIFICATION_METHOD
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

fun ChooseRecoveryNotificationVerificationMethodModel(
  onBack: () -> Unit,
  onSmsClick: (() -> Unit)?,
  onEmailClick: (() -> Unit)?,
): FormBodyModel {
  val baseSubline = "To ensure the safety of your wallet, a verification code is required."
  val selectInstructionsSubline = "Select your preferred method to receive the code."

  return FormBodyModel(
    id = CHOOSE_RECOVERY_NOTIFICATION_VERIFICATION_METHOD,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBack)),
    header =
      FormHeaderModel(
        headline = "Verification Required",
        subline =
          if (onSmsClick != null && onEmailClick != null) {
            "$baseSubline $selectInstructionsSubline"
          } else {
            baseSubline
          }
      ),
    mainContentList =
      immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel =
            ListGroupModel(
              items =
                listOfNotNull(
                  onSmsClick?.let {
                    ListItemModel(
                      leadingAccessory =
                        ListItemAccessory.IconAccessory(
                          model =
                            IconModel(
                              iconImage = IconImage.LocalImage(Icon.SmallIconMessage),
                              iconSize = IconSize.Small
                            )
                        ),
                      title = "SMS",
                      trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
                      onClick = it
                    )
                  },
                  onEmailClick?.let {
                    ListItemModel(
                      leadingAccessory =
                        ListItemAccessory.IconAccessory(
                          model =
                            IconModel(
                              iconImage = IconImage.LocalImage(Icon.SmallIconEmail),
                              iconSize = IconSize.Small
                            )
                        ),
                      title = "Email",
                      trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
                      onClick = it
                    )
                  }
                ).toImmutableList(),
              style = ListGroupStyle.CARD_GROUP_DIVIDER
            )
        )
      ),
    // No primary button, instead the screen advances when a list item is tapped.
    primaryButton = null
  )
}
