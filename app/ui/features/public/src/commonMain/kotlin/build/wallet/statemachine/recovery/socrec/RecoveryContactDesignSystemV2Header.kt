package build.wallet.statemachine.recovery.socrec

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

internal fun recoveryContactDesignSystemV2Header(
  headline: String,
  subline: String?,
  alignment: FormHeaderModel.Alignment = FormHeaderModel.Alignment.LEADING,
) = FormHeaderModel(
  iconModel = IconModel(
    icon = Icon.DotRecoveryContact2,
    iconSize = IconSize.Avatar,
    iconTint = IconTint.InverseBackground
  ),
  headline = headline,
  subline = subline,
  alignment = alignment
)
