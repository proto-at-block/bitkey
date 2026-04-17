package build.wallet.ui.app.partnerships

import build.wallet.analytics.events.screen.id.SellEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.partnerships.purchase.SelectPartnerQuoteBodyModel
import build.wallet.statemachine.partnerships.sell.SellQuotesFormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.CARD_ITEM
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import io.kotest.core.spec.style.FunSpec

class PartnerQuoteComparisonScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("purchase partner quotes comparison screen") {
    paparazzi.snapshot {
      FormScreen(
        model = SelectPartnerQuoteBodyModel(
          title = "Purchase $250.00",
          subTitle = "Offers show the amount you'll receive after exchange fees. Bitkey does not charge a fee.",
          onClosed = {},
          listGroupModel =
            ListGroupModel(
              style = CARD_ITEM,
              items = buyQuoteListItems()
            )
        )
      )
    }
  }

  test("sell partner quotes comparison screen") {
    paparazzi.snapshot {
      FormScreen(
        model =
          SellQuotesFormBodyModel(
            formattedSellAmount = "$250.00",
            mainContentList =
              immutableListOf(
                FormMainContentModel.ListGroup(
                  listGroupModel =
                    ListGroupModel(
                      style = CARD_ITEM,
                      items = sellQuoteListItems()
                    )
                )
              ),
            id = SellEventTrackerScreenId.SELL_QUOTES_LIST,
            onBack = {}
          )
      )
    }
  }
})

private fun buyQuoteListItems() =
  immutableListOf(
    quoteItem(name = "Cash App", sideText = "$250.00", secondarySideText = "10,650 sats"),
    quoteItem(name = "Strike", sideText = "$247.86", secondarySideText = "10,558 sats"),
    quoteItem(name = "Coinbase", sideText = "$246.12", secondarySideText = "10,490 sats")
  )

private fun sellQuoteListItems() =
  immutableListOf(
    quoteItem(name = "Cash App", sideText = "$247.86"),
    quoteItem(name = "Strike", sideText = "$246.12"),
    quoteItem(name = "Coinbase", sideText = "$243.70")
  )

private fun quoteItem(
  name: String,
  sideText: String,
  secondarySideText: String? = null,
) = ListItemModel(
  title = name,
  sideText = sideText,
  secondarySideText = secondarySideText,
  onClick = {},
  leadingAccessory =
    ListItemAccessory.IconAccessory(
      model =
        IconModel(
          icon = Icon.Bitcoin,
          iconSize = IconSize.Large
        )
    ),
  trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30)
)
