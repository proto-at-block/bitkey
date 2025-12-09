package build.wallet.statemachine.partnerships.purchase

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.PARTNER_QUOTES_LIST
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.PARTNER_QUOTE_PROMOTION_INFO_SHEET
import build.wallet.compose.collections.immutableListOf
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PurchaseQuote
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemExplainer
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Model for the screen used to select a quote from our Partners
 *
 * @param [title] the title to display on the screen
 * @param [quotes] list of quotes to display to the user
 * @param [onSelectPartnerQuote] callback fired when the user selects a quote
 * @param [onClosed] callback fired when the user wants to exit the flow
 */
internal fun selectPartnerPurchaseQuoteModel(
  title: String,
  subTitle: String,
  previousPartnerIds: List<PartnerId>,
  quotes: ImmutableList<PurchaseQuoteModel>,
  onSelectPartnerQuote: (PurchaseQuote) -> Unit,
  onClosed: () -> Unit,
  onShowCashAppInfo: () -> Unit,
  isCashAppPromotionEnabled: Boolean,
): BodyModel {
  val listGroupModel =
    ListGroupModel(
      items = quotes
        .sortedWith(
          compareByDescending<PurchaseQuoteModel> { it.quote.partnerInfo.partnerId in previousPartnerIds }
            .thenByDescending { it.quote.cryptoAmount }
        )
        .map { quoteDisplay ->
          val isCashApp = quoteDisplay.quote.partnerInfo.partnerId == PartnerId("CashApp")
          ListItemModel(
            title = quoteDisplay.quote.partnerInfo.name,
            secondaryText = "Previously Used".takeIf {
              quoteDisplay.quote.partnerInfo.partnerId in previousPartnerIds
            },
            sideText = quoteDisplay.fiatDisplayAmount ?: quoteDisplay.bitcoinDisplayAmount,
            secondarySideText = quoteDisplay.fiatDisplayAmount?.let { quoteDisplay.bitcoinDisplayAmount },
            onClick = { onSelectPartnerQuote(quoteDisplay.quote) },
            leadingAccessory = ListItemAccessory.IconAccessory(
              model = IconModel(
                iconImage = when (val url = quoteDisplay.quote.partnerInfo.logoUrl) {
                  null -> IconImage.LocalImage(Icon.Bitcoin)
                  else ->
                    IconImage.UrlImage(
                      url = url,
                      fallbackIcon = Icon.Bitcoin
                    )
                },
                iconSize = IconSize.Large
              )
            ),
            trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
            explainer = when {
              isCashApp && isCashAppPromotionEnabled -> ListItemExplainer(
                title = "No fees, no spread · Ends 12/31",
                iconButton = IconButtonModel(
                  iconModel = IconModel(
                    icon = Icon.SmallIconInformationFilled,
                    iconSize = IconSize.Small,
                    iconTint = IconTint.On30
                  ),
                  onClick = StandardClick(onShowCashAppInfo)
                )
              )
              else -> null
            }
          )
        }.toImmutableList(),
      style = CARD_ITEM
    )
  return SelectPartnerQuoteBodyModel(
    title = title,
    subTitle = subTitle,
    onClosed = onClosed,
    listGroupModel = listGroupModel
  )
}

data class SelectPartnerQuoteBodyModel(
  val title: String,
  val subTitle: String,
  val onClosed: () -> Unit,
  val listGroupModel: ListGroupModel,
) : FormBodyModel(
    onBack = onClosed,
    header = FormHeaderModel(headline = title, subline = subTitle),
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onClosed)
    ),
    mainContentList = immutableListOf(ListGroup(listGroupModel)),
    primaryButton = null,
    id = PARTNER_QUOTES_LIST,
    renderContext = Screen
  )

internal fun cashAppInfoSheetModel(
  cashAppQuote: PurchaseQuote?,
  onDismiss: () -> Unit,
): SheetModel {
  return SheetModel(
    body = CashAppInfoBodyModel(
      cashAppLogoUrl = cashAppQuote?.partnerInfo?.logoUrl,
      onDismiss = onDismiss
    ),
    size = SheetSize.MIN40,
    onClosed = onDismiss
  )
}

data class CashAppInfoBodyModel(
  val cashAppLogoUrl: String?,
  val onDismiss: () -> Unit,
) : FormBodyModel(
    id = PARTNER_QUOTE_PROMOTION_INFO_SHEET,
    onBack = onDismiss,
    toolbar = null,
    header = FormHeaderModel(
      headline = "0% fees, 0% spread",
      subline = "Buy bitcoin from Cash App with no fees or spread from now until December 31.",
      iconModel = cashAppLogoUrl?.let { logo ->
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
      text = "Got it",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = StandardClick(onDismiss)
    ),
    mainContentList = immutableListOf(),
    renderContext = RenderContext.Sheet
  )
