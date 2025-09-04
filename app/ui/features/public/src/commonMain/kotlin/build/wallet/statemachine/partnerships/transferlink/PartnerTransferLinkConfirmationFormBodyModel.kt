package build.wallet.statemachine.partnerships.transferlink

import build.wallet.analytics.events.screen.id.PartnershipsEventTrackerScreenId
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize

/**
 * Form body model for the partner transfer link confirmation sheet.
 *
 * This model presents a confirmation dialog to the user before creating a transfer link
 * with a partner platform (e.g., Strike). It displays partner information and allows
 * the user to either confirm the link creation or cancel the operation.
 */
data class PartnerTransferLinkConfirmationFormBodyModel(
  val partnerInfo: PartnerInfo,
  val onConfirm: () -> Unit,
  val onCancel: () -> Unit,
) : FormBodyModel(
    id = PartnershipsEventTrackerScreenId.PARTNER_TRANSFER_LINK_CONFIRMATION,
    onBack = onCancel,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Link your Bitkey to ${partnerInfo.name}",
      subline = "This allows ${partnerInfo.name} to send bitcoin to your Bitkey wallet. They will not have access to your wallet or keys.",
      iconModel = partnerInfo.logoUrl?.let { logo ->
        IconModel(
          iconImage = IconImage.UrlImage(
            url = logo,
            fallbackIcon = Icon.Bitcoin
          ),
          iconSize = IconSize.Avatar
        )
      } ?: IconModel(Icon.Bitcoin, IconSize.Avatar)
    ),
    primaryButton = ButtonModel(
      text = "Link to ${partnerInfo.name}",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = SheetClosingClick(onConfirm)
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = SheetClosingClick(onCancel)
    ),
    renderContext = RenderContext.Sheet
  )
