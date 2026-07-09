package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.CustomContent
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.FormScreenTitleModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.persistentListOf

open class HardwareConfirmationHelpBodyModel(
  onBack: () -> Unit,
  content: HardwareConfirmationHelpContent,
  devicePlatform: DevicePlatform,
  eventTrackerScreenIdOverride: EventTrackerScreenId? = null,
  eventTrackerContext: EventTrackerContext? = null,
  eventTrackerShouldTrackOverride: Boolean? = null,
) : FormBodyModel(
    id = eventTrackerScreenIdOverride ?: content.eventTrackerScreenId,
    eventTrackerContext = eventTrackerContext,
    eventTrackerShouldTrack = eventTrackerShouldTrackOverride ?: content.eventTrackerShouldTrack,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack)
    ),
    formScreenTitle = FormScreenTitleModel(title = content.headline),
    formScreenLayout = FormScreenLayoutModel.LargeTitle(),
    header = null,
    mainContentList = persistentListOf(
      CustomContent(
        item = HardwareConfirmationHelpContentModel(
          content = content,
          devicePlatform = devicePlatform
        )
      )
    ),
    primaryButton = null
  )
