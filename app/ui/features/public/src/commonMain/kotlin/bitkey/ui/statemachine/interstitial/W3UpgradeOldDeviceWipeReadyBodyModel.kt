package bitkey.ui.statemachine.interstitial

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.Showcase.Content.ImageContent
import build.wallet.statemachine.core.form.FormMainContentModel.Showcase.Content.ImageContent.Image.UPGRADE_W3
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.tokens.LabelType

private const val TITLE = "Wipe and re-gift your old Bitkey"
private const val SUBLINE =
  "Follow the steps to wipe your first generation Bitkey device. " +
    "Then the device can safely be discarded or passed on."

/**
 * Automatic interstitial shown after W3 upgrade sweep safety checks determine the old W1 is ready
 * to wipe.
 */
data class W3UpgradeOldDeviceWipeReadyBodyModel(
  val onWipeOldDevice: () -> Unit,
  val onDone: () -> Unit,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_OLD_DEVICE_WIPE_READY,
    onBack = onDone,
    toolbar = null,
    header = null,
    mainContentList = immutableListOf(),
    primaryButton = ButtonModel(
      text = "Wipe first generation Bitkey",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = SheetClosingClick(onWipeOldDevice)
    ),
    secondaryButton = ButtonModel(
      text = "Done",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = SheetClosingClick(onDone)
    ),
    renderContext = RenderContext.Sheet,
    designSystemV2Model = FormDesignSystemV2Model(
      mainContentList = wipeReadyMainContentList
    )
  )

private val wipeReadyMainContentList = immutableListOf(
  FormMainContentModel.Showcase(
    content = ImageContent(image = UPGRADE_W3),
    fillAvailableSpace = false
  ),
  FormMainContentModel.HeaderBlock(
    header = FormHeaderModel(
      headline = TITLE,
      subline = SUBLINE,
      headlineLabelType = LabelType.Display3
    )
  )
)
