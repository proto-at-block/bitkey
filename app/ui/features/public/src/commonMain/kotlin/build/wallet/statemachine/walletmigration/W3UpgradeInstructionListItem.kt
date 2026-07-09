package build.wallet.statemachine.walletmigration

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.FormScreenTitleModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment
import build.wallet.ui.model.list.ListItemModel

internal const val W3_UPGRADE_INSTRUCTION_HEADER_TO_MAIN_CONTENT_SPACING = 8

internal fun w3UpgradeInstructionListItem(
  title: String,
  secondaryText: String,
  icon: Icon,
) = ListItemModel(
  title = title,
  secondaryText = secondaryText,
  leadingAccessoryAlignment = ListItemAccessoryAlignment.TOP,
  leadingAccessory = ListItemAccessory.IconAccessory(
    model = IconModel(
      icon = icon,
      iconSize = IconSize.Small,
      iconBackgroundType =
        IconBackgroundType.Circle(
          circleSize = IconSize.Custom(48),
          color = IconBackgroundType.Circle.CircleColor.Secondary
        ),
      iconTint = IconTint.Foreground
    )
  )
)

internal fun w3UpgradeStepEyebrow(
  step: Int,
  totalSteps: Int = 4,
) = "Step $step of $totalSteps"

internal fun w3UpgradeInstructionScreenTitle(
  title: String,
  eyebrow: String? = null,
) = FormScreenTitleModel(
  eyebrow = eyebrow,
  title = title
)

internal fun w3UpgradeInstructionHeader(subline: String?) = FormHeaderModel(
  headline = null,
  sublineModel = subline?.let(::StringModel)
)

internal fun w3UpgradeInstructionLayout() = FormScreenLayoutModel.LargeTitle(
  scrollable = false,
  mainContentVerticalAlignment = FormMainContentVerticalAlignment.BOTTOM
)
