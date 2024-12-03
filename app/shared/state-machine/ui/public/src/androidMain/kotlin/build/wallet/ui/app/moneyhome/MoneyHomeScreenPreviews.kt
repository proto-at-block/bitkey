package build.wallet.ui.app.moneyhome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.ProtectedCustomerAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.transactions.PartnerTransactionItemModel
import build.wallet.statemachine.transactions.TransactionItemModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun MoneyHomeScreenFull(
  hideBalance: Boolean = false,
  largeBalance: Boolean = false,
  showSellButton: Boolean = false,
) {
  PreviewWalletTheme {
    MoneyHomeScreen(
      model =
        MoneyHomeBodyModel(
          onSettings = {},
          hideBalance = hideBalance,
          onHideBalance = {},
          balanceModel = if (largeBalance) {
            MoneyAmountModel(
              primaryAmount = "$88,888,888.88",
              secondaryAmount = "153,984,147,317 sats"
            )
          } else {
            MoneyAmountModel(
              primaryAmount = "$289,745",
              secondaryAmount = "424,567 sats"
            )
          },
          cardsModel = MoneyHomeCardsModel(cards = emptyImmutableList()),
          transactionsModel =
            ListModel(
              headerText = "Recent activity",
              sections =
                immutableListOf(
                  ListGroupModel(
                    header = null,
                    style = ListGroupStyle.NONE,
                    items =
                      immutableListOf(
                        TransactionItemModel(
                          truncatedRecipientAddress = "1AH7...CkGJ",
                          date = "Pending",
                          amount = "+ $11.36",
                          amountEquivalent = "0.000105 BTC",
                          transactionType = Incoming,
                          isPending = false,
                          isLate = false,
                          onClick = {}
                        ),
                        TransactionItemModel(
                          truncatedRecipientAddress = "2AH7...CkGJ",
                          date = "Pending",
                          amount = "$21.36",
                          amountEquivalent = "0.000205 BTC",
                          transactionType = Outgoing,
                          isPending = false,
                          isLate = false,
                          onClick = {}
                        ),
                        TransactionItemModel(
                          truncatedRecipientAddress = "3AH7...CkGJ",
                          date = "Pending",
                          amount = "$31.36",
                          amountEquivalent = "0.000305 BTC",
                          transactionType = UtxoConsolidation,
                          isPending = false,
                          isLate = false,
                          onClick = {}
                        ),
                        PartnerTransactionItemModel(
                          title = "Purchase",
                          date = "July 4",
                          logoUrl = null,
                          amount = "$31.36",
                          amountEquivalent = "0.000305 BTC",
                          isPending = false,
                          onClick = {},
                          sideTextTint = ListItemSideTextTint.GREEN,
                          isError = false
                        )
                      )
                  )
                )
            ),
          seeAllButtonModel =
            ButtonModel(
              "See All",
              treatment = ButtonModel.Treatment.Secondary,
              size = ButtonModel.Size.Footer,
              onClick = StandardClick {}
            ),
          coachmark = null,
          buttonsModel =
            MoneyHomeButtonsModel.MoneyMovementButtonsModel(
              addButton =
                MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                  enabled = false,
                  onClick = {}
                ),
              sellButton = if (showSellButton) {
                MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                  enabled = false,
                  onClick = {}
                )
              } else {
                null
              },
              sendButton =
                MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                  enabled = true,
                  onClick = {}
                ),
              receiveButton =
                MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                  enabled = true,
                  onClick = {}
                )
            ),
          refresh = {},
          onRefresh = {},
          isRefreshing = false,
          badgedSettingsIcon = true,
          onOpenPriceDetails = {}
        )
    )
  }
}

@Preview
@Composable
fun MoneyHomeScreenLite() {
  PreviewWalletTheme {
    LiteMoneyHomeScreen(
      model =
        LiteMoneyHomeBodyModel(
          onSettings = {},
          buttonModel = MoneyHomeButtonsModel.SingleButtonModel(onSetUpBitkeyDevice = { }),
          protectedCustomers = immutableListOf(
            ProtectedCustomer(
              "",
              ProtectedCustomerAlias("Alice"),
              setOf(TrustedContactRole.SocialRecoveryContact)
            )
          ),
          badgedSettingsIcon = false,
          onProtectedCustomerClick = {},
          onBuyOwnBitkeyClick = {},
          onAcceptInviteClick = {}
        )
    )
  }
}

@Preview
@Composable
fun MoneyHomeScreenLiteWithoutProtectedCustomers() {
  PreviewWalletTheme {
    LiteMoneyHomeScreen(
      model =
        LiteMoneyHomeBodyModel(
          onSettings = {},
          buttonModel = MoneyHomeButtonsModel.SingleButtonModel(onSetUpBitkeyDevice = { }),
          protectedCustomers = immutableListOf(),
          badgedSettingsIcon = true,
          onProtectedCustomerClick = {},
          onBuyOwnBitkeyClick = {},
          onAcceptInviteClick = {}
        )
    )
  }
}
