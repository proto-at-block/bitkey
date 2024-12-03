package build.wallet.statemachine.inheritance

import androidx.compose.runtime.Stable
import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
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
  val onAcceptInvitation: () -> Unit,
  val selectedTab: ManagingInheritanceTab,
  val hasPendingBeneficiaries: Boolean,
  val benefactors: ListGroupModel,
  val beneficiaries: ListGroupModel,
) : FormBodyModel(
    id = InheritanceEventTrackerScreenId.ManageInheritance,
    enableComposeRendering = true,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      headline = "Manage Inheritance",
      subline = "Manage your beneficiaries and inheritance claims."
    ),
    mainContentList = immutableListOfNotNull(
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
      FormMainContentModel.Callout(
        item = CalloutModel(
          title = "Beneficiary not active",
          subtitle = LabelModel.StringModel("Your contact must accept the invite to be an active beneficiary."),
          leadingIcon = Icon.SmallIconInformationFilled,
          treatment = CalloutModel.Treatment.Information
        )
      ).takeIf {
        selectedTab == ManagingInheritanceTab.Beneficiaries && hasPendingBeneficiaries
      },
      FormMainContentModel.ListGroup(
        when (selectedTab) {
          ManagingInheritanceTab.Inheritance -> benefactors
          ManagingInheritanceTab.Beneficiaries -> beneficiaries
        }
      ),
      SetupInheritanceUpsell(
        icon = Icon.SmallIconInheritance,
        title = "Add a beneficiary",
        body = "Your investment is worth passing on. Add a beneficiary to ensure it stays in good hands.",
        onPrimaryClick = onInviteClick,
        onSecondaryClick = StandardClick { }
      ).takeIf {
        selectedTab == ManagingInheritanceTab.Beneficiaries && beneficiaries.items.isEmpty()
      },
      SetupInheritanceUpsell(
        icon = Icon.SmallIconShieldPerson,
        title = "Become a beneficiary",
        body = "Accept an invite code from your benefactor to get started.",
        onPrimaryClick = StandardClick(onAcceptInvitation),
        onSecondaryClick = StandardClick { }
      ).takeIf {
        selectedTab == ManagingInheritanceTab.Inheritance && benefactors.items.isEmpty()
      }
    ),
    primaryButton = ButtonModel(
      text = "Invite",
      onClick = onInviteClick,
      size = ButtonModel.Size.Footer
    )
  )

@Stable
private fun SetupInheritanceUpsell(
  icon: Icon,
  title: String,
  body: String,
  onPrimaryClick: StandardClick,
  onSecondaryClick: StandardClick,
): FormMainContentModel.Upsell {
  return FormMainContentModel.Upsell(
    title = title,
    body = body,
    iconModel = IconModel(
      icon = icon,
      iconSize = IconSize.Large,
      iconBackgroundType = IconBackgroundType.Circle(
        IconSize.Avatar,
        IconBackgroundType.Circle.CircleColor.InheritanceSurface
      )
    ),
    primaryButton = ButtonModel(
      text = "Add",
      size = ButtonModel.Size.Short,
      onClick = onPrimaryClick,
      leadingIcon = Icon.SmallIconPlus,
      treatment = ButtonModel.Treatment.Accent
    ),
    secondaryButton = ButtonModel(
      text = "Learn more",
      size = ButtonModel.Size.Short,
      onClick = onSecondaryClick,
      treatment = ButtonModel.Treatment.Secondary
    )
  )
}
