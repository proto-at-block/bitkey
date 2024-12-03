package build.wallet.statemachine.partnerships.expected

import build.wallet.analytics.events.screen.id.ExpectedTransactionTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.transactionDeepLink
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class ExpectedTransactionNoticeModel(
  val partnerInfo: PartnerInfo?,
  val transactionDate: String,
  val onViewInPartnerApp: (PartnerRedirectionMethod) -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = ExpectedTransactionTrackerScreenId.EXPECTED_TRANSACTION_NOTICE_DETAILS,
    eventTrackerContext = partnerInfo?.let { PartnerEventTrackerScreenIdContext(it) },
    header = FormHeaderModel(
      iconModel = partnerInfo?.logoUrl?.let { logo ->
        IconModel(
          imageUrl = logo,
          fallbackIcon = Icon.Bitcoin,
          iconSize = IconSize.Avatar
        )
      } ?: IconModel(Icon.Bitcoin, IconSize.Avatar),
      headline = partnerInfo?.name?.let { partner ->
        "Your $partner transfer is on its way to Bitkey"
      } ?: "Your transaction is on its way to Bitkey",
      subline = "The status will update to processing when the transaction is detected in the mempool.",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    onBack = onBack,
    mainContentList = immutableListOf(
      FormMainContentModel.StepperIndicator(
        steps = immutableListOf(
          FormMainContentModel.StepperIndicator.Step(
            style = FormMainContentModel.StepperIndicator.StepStyle.PENDING,
            icon = IconImage.LocalImage(icon = Icon.SmallIconCheck),
            label = "Submitted"
          ),
          FormMainContentModel.StepperIndicator.Step(
            style = FormMainContentModel.StepperIndicator.StepStyle.UPCOMING,
            icon = null,
            label = "Processing"
          ),
          FormMainContentModel.StepperIndicator.Step(
            style = FormMainContentModel.StepperIndicator.StepStyle.UPCOMING,
            icon = null,
            label = "Complete"
          )
        )
      ),
      FormMainContentModel.Divider,
      FormMainContentModel.DataList(
        items = immutableListOf(
          FormMainContentModel.DataList.Data(
            title = "Submitted",
            sideText = transactionDate
          )
        )
      )
    ),
    secondaryButton = partnerInfo?.transactionDeepLink?.let { link ->
      ButtonModel(
        text = "View in ${partnerInfo.name}",
        onClick = StandardClick { onViewInPartnerApp(link) },
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        leadingIcon = Icon.SmallIconArrowUpRight
      )
    },
    primaryButton = ButtonModel(
      text = "Back to Home",
      onClick = StandardClick { onBack() },
      size = ButtonModel.Size.Footer
    ),
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBack)
    ),
    enableComposeRendering = true
  )
