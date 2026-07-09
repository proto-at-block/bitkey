package build.wallet.statemachine.core.form

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

internal fun formHeroIconHeader(
  headline: String,
  subline: String?,
  icon: Icon?,
  iconTint: IconTint? = IconTint.White,
  sublineTreatment: FormHeaderModel.SublineTreatment = FormHeaderModel.SublineTreatment.REGULAR,
  alignment: FormHeaderModel.Alignment = FormHeaderModel.Alignment.LEADING,
) = FormHeaderModel(
  headline = headline,
  subline = subline,
  iconModel = icon?.let {
    IconModel(
      icon = it,
      iconSize = IconSize.Large,
      iconBackgroundType = IconBackgroundType.Circle(
        circleSize = IconSize.Avatar,
        color = IconBackgroundType.Circle.CircleColor.Hero
      ),
      iconTint = iconTint
    )
  },
  sublineTreatment = sublineTreatment,
  alignment = alignment
)

internal fun formWarningIconHeader(
  headline: String,
  subline: String?,
  sublineTreatment: FormHeaderModel.SublineTreatment = FormHeaderModel.SublineTreatment.REGULAR,
  alignment: FormHeaderModel.Alignment = FormHeaderModel.Alignment.LEADING,
) = FormHeaderModel(
  headline = headline,
  subline = subline,
  iconModel = formWarningIconModel(),
  sublineTreatment = sublineTreatment,
  alignment = alignment
)

internal fun resolveLegacyHeaderWarningIconModel(iconModel: IconModel?): IconModel? =
  when {
    iconModel.isLegacyWarningHeaderIcon() -> {
      formWarningIconModel()
    }
    else -> iconModel
  }

private fun formWarningIconModel() =
  IconModel(
    icon = Icon.CriticalBadgeAlert,
    iconSize = IconSize.Large,
    iconBackgroundType = IconBackgroundType.Circle(
      circleSize = IconSize.Avatar,
      color = IconBackgroundType.Circle.CircleColor.InverseBackground
    ),
    iconTint = IconTint.Background
  )

private fun IconModel?.isLegacyWarningHeaderIcon(): Boolean {
  if (this == null) return false

  val icon = (iconImage as? IconImage.LocalImage)?.icon ?: return false
  return iconBackgroundType == IconBackgroundType.Transient &&
    iconSize == IconSize.Avatar &&
    icon.isLegacyWarningIcon()
}

private fun Icon.isLegacyWarningIcon(): Boolean {
  return this == Icon.LargeIconWarningFilled
}
