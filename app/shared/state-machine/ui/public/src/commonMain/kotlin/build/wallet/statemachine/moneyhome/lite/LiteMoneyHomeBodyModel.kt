package build.wallet.statemachine.moneyhome.lite

import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsModel
import build.wallet.statemachine.moneyhome.lite.card.BuyOwnBitkeyMoneyHomeCardModel
import build.wallet.statemachine.moneyhome.lite.card.WalletsProtectingMoneyHomeCardModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

fun LiteMoneyHomeBodyModel(
  onSettings: () -> Unit,
  primaryAmountString: String,
  secondaryAmountString: String,
  onSetUpBitkeyDevice: () -> Unit,
  protectedCustomers: ImmutableList<ProtectedCustomer>,
  onProtectedCustomerClick: (ProtectedCustomer) -> Unit,
  onBuyOwnBitkeyClick: () -> Unit,
  onAcceptInviteClick: () -> Unit,
) = MoneyHomeBodyModel(
  onSettings = onSettings,
  balanceModel =
    MoneyAmountModel(
      primaryAmount = primaryAmountString,
      secondaryAmount = secondaryAmountString
    ),
  cardsModel =
    MoneyHomeCardsModel(
      cards =
        listOf(
          // Wallets you're Protecting card
          WalletsProtectingMoneyHomeCardModel(
            protectedCustomers = protectedCustomers,
            onProtectedCustomerClick = onProtectedCustomerClick,
            onAcceptInviteClick = onAcceptInviteClick
          ),
          // Buy your Own Bitkey card
          BuyOwnBitkeyMoneyHomeCardModel(onClick = onBuyOwnBitkeyClick)
        ).toImmutableList()
    ),
  transactionsModel = null,
  seeAllButtonModel = null,
  buttonsModel =
    MoneyHomeButtonsModel.SingleButtonModel(
      onSetUpBitkeyDevice = onSetUpBitkeyDevice
    ),
  refresh = {},
  onRefresh = {},
  isRefreshing = false
)
