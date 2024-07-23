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
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

fun ExpectedTransactionNoticeModel(
  partnerInfo: PartnerInfo?,
  transactionDate: String,
  onViewInPartnerApp: (PartnerRedirectionMethod) -> Unit,
  onBack: () -> Unit,
) = FormBodyModel(
  id = ExpectedTransactionTrackerScreenId.EXPECTED_TRANSACTION_NOTICE_DETAILS,
  eventTrackerContext = partnerInfo?.let { PartnerEventTrackerScreenIdContext(it) },
  header = FormHeaderModel(
    iconModel = partnerInfo?.logoUrl?.let { logo ->
      IconModel(
        imageUrl = logo,
        fallbackIcon = Icon.Bitcoin,
        iconSize = IconSize.XLarge
      )
    } ?: IconModel(Icon.Bitcoin, IconSize.Large),
    headline = partnerInfo?.name?.let { partner ->
      "Your $partner transfer is on its way to Bitkey"
    } ?: "Your transaction is on its way to Bitkey",
    subline = "The status will update to processing when the transaction is detected in the mempool.",
    alignment = FormHeaderModel.Alignment.CENTER
  ),
  onBack = onBack,
  mainContentList = immutableListOf(
    FormMainContentModel.Spacer(height = 1f),
    FormMainContentModel.StepperIndicator(
      progress = .20f,
      labels = immutableListOf("Submitted", "Processing", "Complete")
    ),
    FormMainContentModel.Spacer(height = 1f),
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
      text = "View on ${partnerInfo.name}",
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
  )
)
