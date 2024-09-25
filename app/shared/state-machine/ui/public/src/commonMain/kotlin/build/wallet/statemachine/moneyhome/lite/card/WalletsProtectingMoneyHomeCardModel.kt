package build.wallet.statemachine.moneyhome.lite.card

import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.socrec.list.listItemModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

const val WALLETS_YOURE_PROTECTING_MESSAGE = "Wallets you’re protecting"
const val NOT_PROTECTING_ANY_WALLETS = "You're not protecting any wallets"

fun WalletsProtectingMoneyHomeCardModel(
  protectedCustomers: ImmutableList<ProtectedCustomer>,
  onProtectedCustomerClick: (ProtectedCustomer) -> Unit,
  onAcceptInviteClick: () -> Unit,
  isLiteMode: Boolean = false,
): CardModel {
  var cardItems = protectedCustomers.map { protectedCustomer ->
    protectedCustomer.listItemModel {
      onProtectedCustomerClick(it)
    }
  }

  val title = if (isLiteMode) {
    with(
      NOT_PROTECTING_ANY_WALLETS.takeIf { cardItems.isEmpty() }
        ?: WALLETS_YOURE_PROTECTING_MESSAGE
    ) {
      LabelModel.StringWithStyledSubstringModel.from(
        this,
        mapOf(this to LabelModel.Color.ON60)
      )
    }
  } else {
    LabelModel.StringWithStyledSubstringModel.from(WALLETS_YOURE_PROTECTING_MESSAGE, mapOf())
  }
  if (isLiteMode) {
    if (cardItems.isEmpty()) {
      cardItems = cardItems.plus(getAcceptInviteItemModel(protectedCustomers, onAcceptInviteClick))
    }
  } else {
    cardItems = cardItems.plus(getAcceptInviteItemModel(protectedCustomers, onAcceptInviteClick))
  }

  return CardModel(
    title = title,
    content = CardModel.CardContent.DrillList(items = cardItems.toImmutableList()),
    style = CardModel.CardStyle.Outline
  )
}

private fun getAcceptInviteItemModel(
  protectedCustomers: ImmutableList<ProtectedCustomer>,
  onAcceptInviteClick: () -> Unit,
) = ListItemModel(
  // Hack to get a footer button in a DrillList
  title = "",
  leadingAccessory =
    ButtonAccessory(
      model =
        ButtonModel(
          text = if (protectedCustomers.isEmpty()) "Accept invite" else "Accept another invite",
          onClick = StandardClick(onAcceptInviteClick),
          treatment = ButtonModel.Treatment.Secondary,
          size = ButtonModel.Size.Footer
        )
    )
)
