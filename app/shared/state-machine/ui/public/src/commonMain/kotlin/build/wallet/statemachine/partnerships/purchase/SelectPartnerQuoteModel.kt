package build.wallet.statemachine.partnerships.purchase

import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId.PARTNER_QUOTES_LIST
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.partnerships.Quote
import build.wallet.f8e.partnerships.bitcoinAmount
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize.FULL
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Model for the screen used to select a quote from our Partners
 *
 * @param [title] the title to display on the screen
 * @param [moneyDisplayFormatter] used to format the amounts for display
 * @param [quotes] list of quotes to display to the user
 * @param [onSelectPartnerQuote] callback fired when the user selects a quote
 * @param [onBack] callback fired when the user wants to go back in the flow
 */
fun selectPartnerQuoteModel(
  title: String,
  subTitle: String,
  moneyDisplayFormatter: MoneyDisplayFormatter,
  quotes: ImmutableList<Quote>,
  onSelectPartnerQuote: (Quote) -> Unit,
  onBack: () -> Unit,
): SheetModel {
  val listGroupModel =
    ListGroupModel(
      items =
        quotes.map {
          ListItemModel(
            title = it.partnerInfo.name,
            sideText = moneyDisplayFormatter.format(it.bitcoinAmount()),
            onClick = { onSelectPartnerQuote(it) },
            leadingAccessory =
              ListItemAccessory.IconAccessory(
                model =
                  IconModel(
                    iconImage =
                      when (val url = it.partnerInfo.logoUrl) {
                        null -> IconImage.LocalImage(Icon.Bitcoin)
                        else ->
                          IconImage.UrlImage(
                            url = url,
                            fallbackIcon = Icon.Bitcoin
                          )
                      },
                    iconSize = IconSize.Regular
                  )
              )
          )
        }.toImmutableList(),
      style = CARD_ITEM
    )
  return SheetModel(
    body =
      FormBodyModel(
        onBack = onBack,
        header = FormHeaderModel(headline = title, subline = subTitle, alignment = FormHeaderModel.Alignment.CENTER),
        toolbar = null,
        mainContentList = immutableListOf(ListGroup(listGroupModel)),
        primaryButton = null,
        id = PARTNER_QUOTES_LIST,
        renderContext = Screen
      ),
    size = FULL,
    dragIndicatorVisible = true,
    onClosed = onBack
  )
}
