package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.Showcase.Content.ImageContent
import build.wallet.statemachine.core.form.FormMainContentModel.Showcase.Content.ImageContent.Image.UPGRADE_W3
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel

/**
 * W3 upgrade success sheet.
 */
data class W3UpgradeCompleteSheetBodyModel(
  override val onBack: () -> Unit,
  val onDone: () -> Unit,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_COMPLETE,
    onBack = onBack,
    toolbar = null,
    header = null,
    mainContentList = w3UpgradeCompleteMainContentList,
    primaryButton = ButtonModel(
      text = "Done",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = SheetClosingClick(onDone)
    ),
    renderContext = RenderContext.Sheet
  )

private val w3UpgradeCompleteMainContentList =
  build.wallet.compose.collections.immutableListOf(
    FormMainContentModel.Showcase(
      content = ImageContent(UPGRADE_W3),
      fillAvailableSpace = false
    ),
    FormMainContentModel.HeaderBlock(
      header = w3UpgradeCompleteHeaderModel()
    )
  )

private fun w3UpgradeCompleteHeaderModel() =
  FormHeaderModel(
    headline = "You've upgraded to the new Bitkey",
    subline = "Your new wallet is ready to use.",
    iconModel = null
  )

fun W3UpgradeCompleteSheetModel(onDone: () -> Unit) =
  W3UpgradeCompleteSheetBodyModel(
    onBack = onDone,
    onDone = onDone
  ).asSheetModalScreen(onClosed = onDone)
