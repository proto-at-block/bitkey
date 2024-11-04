package build.wallet.statemachine.inheritance

import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.tab.CircularTabRowModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

enum class ManagingInheritanceTab {
  Inheritance,
  Beneficiaries,
}

data class ManagingInheritanceBodyModel(
  override val onBack: () -> Unit,
  val onInviteClick: StandardClick,
  val onTabRowClick: (ManagingInheritanceTab) -> Unit,
  val content: List<ListGroupModel>,
  val selectedTab: ManagingInheritanceTab,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.ManageInheritance,
    enableComposeRendering = true,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory { onBack() }
    ),
    header = FormHeaderModel(
      headline = "Manage Inheritance",
      subline = "Manage your beneficiaries and inheritance claims."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.CircularTabRow(
        item = CircularTabRowModel(
          items =
            ManagingInheritanceTab.entries.map {
              when (it) {
                ManagingInheritanceTab.Inheritance -> "Inheritance"
                ManagingInheritanceTab.Beneficiaries -> "Beneficiaries"
              }
            },
          selectedItemIndex = selectedTab.ordinal,
          onClick = { index -> onTabRowClick(ManagingInheritanceTab.entries[index]) },
          key = "InheritanceTabRow"
        )
      ),
      FormMainContentModel.ListGroup(
        content[selectedTab.ordinal]
      )
    ),
    primaryButton = ButtonModel(
      text = "Invite",
      onClick = onInviteClick,
      size = ButtonModel.Size.Footer
    )
  )
