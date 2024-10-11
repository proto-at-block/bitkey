package build.wallet.statemachine.moneyhome.full

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

data class ViewingAddTrustedContactFormBodyModel(
  val onAddTrustedContact: () -> Unit,
  val onSkip: () -> Unit,
  val onClosed: () -> Unit,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_EXPLAINER,
    onBack = onClosed,
    toolbar = null,
    header = FormHeaderModel(
      iconModel = IconModel(
        icon = Icon.LargeIconShieldPerson,
        iconSize = IconSize.Large,
        iconTint = IconTint.Primary,
        iconBackgroundType = IconBackgroundType.Circle(
          circleSize = IconSize.Avatar,
          color = IconBackgroundType.Circle.CircleColor.PrimaryBackground20
        ),
        iconTopSpacing = 0
      ),
      headline = "Trusted Contacts",
      alignment = FormHeaderModel.Alignment.LEADING,
      subline =
        """
            Trusted Contacts can help you recover your wallet if you lose access. Instead of relying on a company, you depend on the people you know and trust.
            
            Trusted Contacts won’t have access to your wallet or funds. They’re only able to help you recover your wallet.
        """.trimIndent()
    ),
    primaryButton = ButtonModel(
      text = "Add Trusted Contact",
      size = ButtonModel.Size.Footer,
      onClick = SheetClosingClick(onAddTrustedContact)
    ),
    secondaryButton = ButtonModel(
      text = "Skip",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = SheetClosingClick(onSkip)
    ),
    renderContext = RenderContext.Sheet
  )
