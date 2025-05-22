package build.wallet.statemachine.account

import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.BEING_TRUSTED_CONTACT_INTRODUCTION
import build.wallet.compose.collections.immutableListOf
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

private const val SAVE_RECOVERY_KEY_BODY_ANDROID =
  "We’ll save a recovery key for your contact’s wallet to your Google Drive account."
private const val SAVE_RECOVERY_KEY_BODY_IOS =
  "We’ll save a recovery key for your contact’s wallet to your iCloud account."

data class BeTrustedContactIntroductionModel(
  val devicePlatform: DevicePlatform,
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBack)),
    header = FormHeaderModel(
      headline = "You've been invited to become a Recovery Contact",
      subline = "A Recovery Contact can help someone restore a wallet that they may have lost access to."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.Explainer(
        items = immutableListOf(
          FormMainContentModel.Explainer.Statement(
            leadingIcon = Icon.SmallIconTicket,
            title = "Accept an invite",
            body = "You will need to enter the code your contact sent you to start safeguarding their wallet."
          ),
          FormMainContentModel.Explainer.Statement(
            leadingIcon = Icon.SmallIconCloud,
            title = "Save a recovery key",
            body = when (devicePlatform) {
              DevicePlatform.Jvm, DevicePlatform.Android -> SAVE_RECOVERY_KEY_BODY_ANDROID
              DevicePlatform.IOS -> SAVE_RECOVERY_KEY_BODY_IOS
            }
          ),
          FormMainContentModel.Explainer.Statement(
            leadingIcon = Icon.SmallIconCheckStroked,
            title = "Sit back and relax",
            body = "If your contact ever needs your help, they’ll reach out to you with a code you can enter using this app to assist in their recovery."
          )
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      onClick = StandardClick(onContinue),
      size = ButtonModel.Size.Footer
    ),
    onBack = onBack,
    id = BEING_TRUSTED_CONTACT_INTRODUCTION
  )
