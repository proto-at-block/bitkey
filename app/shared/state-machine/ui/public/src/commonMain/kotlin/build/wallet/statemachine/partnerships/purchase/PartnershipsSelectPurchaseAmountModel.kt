package build.wallet.statemachine.partnerships.purchase

import build.wallet.compose.collections.immutableListOf
import build.wallet.money.FiatMoney
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SheetSize
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTitleAlignment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Model for the screen used to select the amount of Bitcoin
 * users want to purchase from our Partners
 *
 * @param [purchaseAmounts] list of amounts to display to the user
 * @param [selectedAmount] the amount the user has selected
 * @param [moneyDisplayFormatter] used to format the amounts for display
 * @param [onSelectAmount] callback fired when the user selects an amount
 * @param [onSelectCustomAmount] callback fired when the user selects the custom amount option
 * @param [onNext] callback fired when the user taps the "Next" button
 * @param [onBack] callback fired when the user wants to go back in the flow
 * @param [onExit] callback fired when the user wants to exit the flow
 */
fun selectPurchaseAmountModel(
  purchaseAmounts: ImmutableList<FiatMoney>,
  selectedAmount: FiatMoney?,
  moneyDisplayFormatter: MoneyDisplayFormatter,
  onSelectAmount: (FiatMoney) -> Unit,
  onSelectCustomAmount: () -> Unit,
  onNext: (FiatMoney) -> Unit,
  onBack: () -> Unit,
  onExit: () -> Unit,
): SheetModel {
  val items =
    purchaseAmounts.map {
      ListItemModel(
        title = moneyDisplayFormatter.formatCompact(it),
        titleAlignment = ListItemTitleAlignment.CENTER,
        onClick = { onSelectAmount(it) },
        selected = it == selectedAmount
      )
    }.toMutableList()

  items.add(
    ListItemModel(
      title = "...",
      titleAlignment = ListItemTitleAlignment.CENTER,
      onClick = onSelectCustomAmount,
      selected = selectedAmount?.let { !purchaseAmounts.contains(it) } ?: false
    )
  )

  return SheetModel(
    body =
      FormBodyModel(
        id = null,
        header =
          FormHeaderModel(
            headline = "Choose an amount",
            alignment = FormHeaderModel.Alignment.CENTER
          ),
        onBack = onBack,
        toolbar = null,
        mainContentList =
          immutableListOf(
            FormMainContentModel.ListGroup(
              listGroupModel =
                ListGroupModel(
                  style = ListGroupStyle.THREE_COLUMN_CARD_ITEM,
                  items = items.toImmutableList()
                )
            )
          ),
        primaryButton =
          ButtonModel(
            text = "Next",
            isEnabled = selectedAmount != null,
            size = Footer,
            onClick =
              Click.standardClick {
                selectedAmount?.let { onNext(selectedAmount) }
              }
          ),
        secondaryButton = null,
        keepScreenOn = false,
        onLoaded = {},
        eventTrackerScreenIdContext = null,
        eventTrackerShouldTrack = false,
        renderContext = RenderContext.Sheet
      ),
    onClosed = onExit,
    size = SheetSize.MIN40,
    dragIndicatorVisible = true
  )
}
