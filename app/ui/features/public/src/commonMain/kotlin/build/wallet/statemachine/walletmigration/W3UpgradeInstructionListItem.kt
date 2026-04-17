package build.wallet.statemachine.walletmigration

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemAccessoryAlignment
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.ImmutableList

internal fun w3UpgradeLegacyInstructionListItem(
  title: String,
  secondaryText: String,
  icon: Icon,
) = ListItemModel(
  title = title,
  secondaryText = secondaryText,
  leadingAccessory = ListItemAccessory.IconAccessory(
    model = IconModel(
      icon = icon,
      iconSize = IconSize.Small,
      iconTint = IconTint.Foreground
    )
  )
)

internal fun w3UpgradeDesignSystemV2InstructionListItem(
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

internal fun w3UpgradeInstructionDesignSystemV2Model(
  title: String,
  subline: String?,
  mainContentList: ImmutableList<FormMainContentModel>,
  eyebrow: String? = null,
) = FormDesignSystemV2Model(
  eyebrow = eyebrow,
  title = title,
  header = FormHeaderModel(
    headline = null,
    sublineModel = subline?.let(::StringModel)
  ),
  mainContentList = mainContentList,
  headerToMainContentSpacing = 8,
  scrollable = false,
  mainContentVerticalAlignment = FormDesignSystemV2Model.MainContentVerticalAlignment.BOTTOM
)
