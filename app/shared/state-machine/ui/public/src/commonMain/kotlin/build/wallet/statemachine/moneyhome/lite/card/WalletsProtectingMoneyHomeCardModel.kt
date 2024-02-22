package build.wallet.statemachine.moneyhome.lite.card

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.socrec.list.listItemModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

const val WALLETS_YOURE_PROTECTING_MESSAGE = "Wallets you’re protecting"

fun WalletsProtectingMoneyHomeCardModel(
  protectedCustomers: ImmutableList<ProtectedCustomer>,
  onProtectedCustomerClick: (ProtectedCustomer) -> Unit,
  onAcceptInviteClick: () -> Unit,
): CardModel {
  return CardModel(
    title =
      LabelModel.StringWithStyledSubstringModel.from(
        WALLETS_YOURE_PROTECTING_MESSAGE,
        emptyMap()
      ),
    content =
      CardModel.CardContent.DrillList(
        items =
          protectedCustomers.map { protectedCustomer ->
            protectedCustomer.listItemModel {
              onProtectedCustomerClick(it)
            }
          }.plus(
            ListItemModel(
              // Hack to get a footer button in a DrillList
              title = "",
              leadingAccessory =
                ButtonAccessory(
                  model =
                    ButtonModel(
                      text = "Accept Invite",
                      onClick = Click.standardClick(onAcceptInviteClick),
                      treatment = ButtonModel.Treatment.Secondary,
                      size = ButtonModel.Size.Footer
                    )
                )
            )
          ).toImmutableList()
      ),
    style = CardModel.CardStyle.Outline
  )
}
