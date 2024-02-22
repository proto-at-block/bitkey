package build.wallet.statemachine.core.form

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.REGULAR
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

data class FormHeaderModel(
  /** Optional icon shown large at the top of the screen. */
  val icon: Icon? = null,
  /** Text shown large at the top of the screen. */
  val headline: String,
  /** Optional subline shown below the headline. */
  val sublineModel: LabelModel.StringWithStyledSubstringModel? = null,
  val sublineTreatment: SublineTreatment = REGULAR,
  val alignment: Alignment = LEADING,
) {
  constructor(
    icon: Icon? = null,
    headline: String,
    subline: String?,
    sublineTreatment: SublineTreatment = REGULAR,
    alignment: Alignment = LEADING,
  ) : this(
    icon = icon,
    headline = headline,
    sublineModel =
      subline?.let {
        LabelModel.StringWithStyledSubstringModel.from(
          string = it,
          substringToColor = emptyMap()
        )
      },
    sublineTreatment = sublineTreatment,
    alignment = alignment
  )

  enum class Alignment {
    LEADING,
    CENTER,
  }

  enum class SublineTreatment {
    REGULAR,
    MONO,
  }

  val iconModel: IconModel?
    get() = icon?.let {
      IconModel(
        icon = icon,
        iconSize = IconSize.Avatar,
        iconTint = IconTint.Primary
      )
    }
}
