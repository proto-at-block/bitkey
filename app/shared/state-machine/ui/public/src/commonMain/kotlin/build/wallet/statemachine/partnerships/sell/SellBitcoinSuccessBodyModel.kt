package build.wallet.statemachine.partnerships.sell

import build.wallet.analytics.events.screen.id.SellEventTrackerScreenId
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class SellBitcoinSuccessBodyModel(
  val partnerInfo: PartnerInfo?,
  override val onBack: (() -> Unit),
) : FormBodyModel(
    id = SellEventTrackerScreenId.SELL_PARTNERS_ON_THE_WAY_SUCCESS,
    onBack = { onBack() },
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory {
        onBack()
      }
    ),
    header = FormHeaderModel(
      headline = "Your bitcoin is on the way to MoonPay to sell",
      subline = null,
      iconModel = null,
      sublineTreatment = FormHeaderModel.SublineTreatment.REGULAR,
      alignment = FormHeaderModel.Alignment.LEADING,
      customContent = FormHeaderModel.CustomContent.PartnershipTransferAnimation(
        partnerIcon = IconModel(
          iconImage =
            when (val url = partnerInfo?.logoUrl) {
              null -> LocalImage(Bitcoin)
              else ->
                UrlImage(
                  url = url,
                  fallbackIcon = Bitcoin
                )
            },
          iconSize = IconSize.Avatar
        )
      )
    ),
    mainContentList = emptyImmutableList(),
    primaryButton = ButtonModel(
      text = "Done",
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = { onBack() }
    ),
    renderContext = RenderContext.Screen
  )
