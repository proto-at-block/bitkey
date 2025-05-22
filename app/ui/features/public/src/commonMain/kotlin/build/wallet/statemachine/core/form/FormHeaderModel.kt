package build.wallet.statemachine.core.form

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.REGULAR
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import dev.zacsweers.redacted.annotations.Redacted

data class FormHeaderModel(
  /** Optional icon shown large at the top of the screen. */
  val iconModel: IconModel? = null,
  /** Text shown large at the top of the screen. */
  val headline: String?,
  /** Optional subline shown below the headline. */
  @Suppress("ktlint:standard:no-consecutive-comments")
  // TODO [W-6168] Currently, the transaction detail, send confirmation, and send success screens
  //  use sublineModel to show the wallet address the customer sends to. We redact this outright as
  //  a temporary measure, but should endeavor to build a better solution to redact without
  //  affecting a generic model like FormHeaderModel
  @Redacted val sublineModel: LabelModel? = null,
  val sublineTreatment: SublineTreatment = REGULAR,
  val alignment: Alignment = LEADING,
  val customContent: CustomContent? = null,
) {
  constructor(
    headline: String,
    subline: String?,
    iconModel: IconModel?,
    sublineTreatment: SublineTreatment = REGULAR,
    alignment: Alignment = LEADING,
    customContent: CustomContent? = null,
  ) : this(
    iconModel = iconModel,
    headline = headline,
    sublineModel = subline?.let { StringModel(it) },
    sublineTreatment = sublineTreatment,
    alignment = alignment,
    customContent = customContent
  )

  constructor(
    icon: Icon? = null,
    headline: String,
    subline: String?,
    sublineTreatment: SublineTreatment = REGULAR,
    alignment: Alignment = LEADING,
  ) : this(
    iconModel = icon?.let {
      IconModel(
        icon = icon,
        iconSize = IconSize.Avatar,
        iconTint = IconTint.Primary
      )
    },
    headline = headline,
    subline = subline,
    sublineTreatment = sublineTreatment,
    alignment = alignment
  )

  constructor(
    icon: Icon?,
    headline: String,
    sublineModel: LabelModel? = null,
    sublineTreatment: SublineTreatment = REGULAR,
    alignment: Alignment = LEADING,
  ) : this(
    iconModel = icon?.let {
      IconModel(
        icon = icon,
        iconSize = IconSize.Avatar,
        iconTint = IconTint.Primary
      )
    },
    headline,
    sublineModel,
    sublineTreatment,
    alignment
  )

  enum class Alignment {
    LEADING,
    CENTER,
  }

  enum class SublineTreatment {
    REGULAR,
    SMALL,
    MONO,
  }

  sealed class CustomContent {
    data class PartnershipTransferAnimation(
      val bitkeyIcon: IconModel = IconModel(
        icon = Icon.BitkeyLogo,
        iconSize = IconSize.Avatar,
        iconTint = IconTint.Foreground,
        iconOpacity = null
      ),
      val partnerIcon: IconModel,
    ) : CustomContent()
  }

  data class PosterImage(
    val icon: Icon,
  ) : CustomContent()
}
