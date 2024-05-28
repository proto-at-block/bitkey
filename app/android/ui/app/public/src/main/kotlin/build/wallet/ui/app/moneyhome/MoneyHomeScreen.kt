package build.wallet.ui.app.moneyhome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.MoneyHomeCardsModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.transactions.TransactionItemModel
import build.wallet.ui.app.moneyhome.card.MoneyHomeCard
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.ButtonContentsList
import build.wallet.ui.components.button.RowOfButtons
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.refresh.PullRefreshIndicator
import build.wallet.ui.components.refresh.pullRefresh
import build.wallet.ui.components.toolbar.ToolbarAccessory
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Treatment
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun MoneyHomeScreen(model: MoneyHomeBodyModel) {
  val listState = rememberLazyListState()
  Column {
    Box(
      modifier =
        Modifier
          .pullRefresh(
            refreshing = model.isRefreshing,
            onRefresh = model.onRefresh
          )
    ) {
      LazyColumn(
        modifier =
          Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = listState
      ) {
        // Header
        item {
          Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Header(
              headline = "Home",
              headlineTopSpacing = 8.dp,
              fillsMaxWidth = false
            )
            Spacer(Modifier.weight(1F))
            ToolbarAccessory(model.trailingToolbarAccessoryModel)
          }
          Spacer(Modifier.height(48.dp))
        }

        // Balance + buttons
        item {
          with(model.balanceModel) {
            HeroAmount(
              modifier = Modifier.clickable(
                interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = { model.onHideBalance() }
              ),
              primaryAmount = AnnotatedString(primaryAmount),
              secondaryAmountWithCurrency = secondaryAmount,
              hideBalance = model.hideBalance
            )
          }
          Spacer(Modifier.height(4.dp))
          MoneyHomeButtons(model = model.buttonsModel)
          Spacer(Modifier.height(40.dp))
        }

        // No UI between the action buttons and the tx list so show a divider
        if (model.cardsModel.cards.isEmpty() && model.transactionsModel != null) {
          item {
            Divider(
              modifier =
                Modifier
                  .padding(horizontal = 20.dp)
                  .padding(top = 16.dp)
            )
          }
        }

        // Cards
        items(model.cardsModel.cards) { cardModel ->
          MoneyHomeCard(
            modifier = Modifier.padding(horizontal = 20.dp),
            model = cardModel
          )
          Spacer(modifier = Modifier.height(24.dp))
        }

        model.transactionsModel?.let { transactionsModel ->
          item {
            Transactions(
              model = transactionsModel,
              seeAllButtonModel = model.seeAllButtonModel,
              hideValue = model.hideBalance
            )
          }
        }
      }

      PullRefreshIndicator(
        modifier = Modifier.align(Alignment.TopCenter),
        refreshing = model.isRefreshing,
        onRefresh = model.onRefresh
      )
    }
  }
}

@Composable
fun LiteMoneyHomeScreen(model: LiteMoneyHomeBodyModel) {
  val listState = rememberLazyListState()
  Column {
    Box {
      LazyColumn(
        modifier =
          Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = listState
      ) {
        // Header
        item {
          Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Header(
              headline = "Home",
              headlineTopSpacing = 8.dp,
              fillsMaxWidth = false
            )
            Spacer(Modifier.weight(1F))
            ToolbarAccessory(model.trailingToolbarAccessoryModel)
          }
        }

        // Cards
        items(model.cardsModel.cards) { cardModel ->
          Spacer(Modifier.height(40.dp))
          MoneyHomeCard(
            modifier = Modifier.padding(horizontal = 20.dp),
            model = cardModel
          )
        }

        item {
          MoneyHomeButtons(model = model.buttonsModel)
        }
      }
    }
  }
}

@Composable
private fun MoneyHomeButtons(model: MoneyHomeButtonsModel) {
  when (model) {
    is MoneyHomeButtonsModel.MoneyMovementButtonsModel ->
      CircularActions(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .padding(horizontal = 20.dp),
        buttonContentsList =
          ButtonContentsList(
            buttonContents =
              model.buttons.map {
                {
                  IconButton(model = it)
                }
              }
          )
      )

    is MoneyHomeButtonsModel.SingleButtonModel ->
      Button(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = 40.dp)
            .padding(horizontal = 20.dp),
        model = model.button
      )
  }
}

@Composable
private fun CircularActions(
  modifier: Modifier = Modifier,
  buttonContentsList: ButtonContentsList,
) {
  RowOfButtons(
    modifier = modifier,
    buttonContents = buttonContentsList,
    interButtonSpacing = 40.dp // 20.dp of spacing on either side of each button
  )
}

@Composable
private fun Transactions(
  model: ListModel,
  seeAllButtonModel: ButtonModel?,
  hideValue: Boolean,
) {
  Column(
    modifier =
      Modifier
        .padding(horizontal = 20.dp)
  ) {
    TransactionList(
      modifier =
        Modifier
          .fillMaxWidth(),
      model = model,
      hideValue = hideValue
    )
    seeAllButtonModel?.let {
      Button(model = seeAllButtonModel)
    }
  }
}

@Preview
@Composable
internal fun MoneyHomeScreenFull(hideBalance: Boolean = false) {
  PreviewWalletTheme {
    MoneyHomeScreen(
      model =
        MoneyHomeBodyModel(
          onSettings = {},
          hideBalance = hideBalance,
          onHideBalance = {},
          balanceModel =
            MoneyAmountModel(
              primaryAmount = "$289,745",
              secondaryAmount = "424,567 sats"
            ),
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
                          incoming = true,
                          isPending = false,
                          onClick = {}
                        ),
                        TransactionItemModel(
                          truncatedRecipientAddress = "2AH7...CkGJ",
                          date = "Pending",
                          amount = "$21.36",
                          amountEquivalent = "0.000205 BTC",
                          incoming = false,
                          isPending = false,
                          onClick = {}
                        )
                      )
                  )
                )
            ),
          seeAllButtonModel =
            ButtonModel(
              "See All",
              treatment = Treatment.Secondary,
              size = Size.Footer,
              onClick = StandardClick {}
            ),
          buttonsModel =
            MoneyHomeButtonsModel.MoneyMovementButtonsModel(
              sendButton =
                MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                  enabled = true,
                  onClick = {}
                ),
              receiveButton =
                MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                  enabled = true,
                  onClick = {}
                ),
              addButton =
                MoneyHomeButtonsModel.MoneyMovementButtonsModel.Button(
                  enabled = false,
                  onClick = {}
                )
            ),
          refresh = {},
          onRefresh = {},
          isRefreshing = false
        )
    )
  }
}

@Preview
@Composable
internal fun MoneyHomeScreenLite() {
  PreviewWalletTheme {
    LiteMoneyHomeScreen(
      model =
        LiteMoneyHomeBodyModel(
          onSettings = {},
          buttonModel = MoneyHomeButtonsModel.SingleButtonModel(onSetUpBitkeyDevice = { }),
          protectedCustomers = immutableListOf(
            ProtectedCustomer("", ProtectedCustomerAlias("Alice"))
          ),
          onProtectedCustomerClick = {},
          onBuyOwnBitkeyClick = {},
          onAcceptInviteClick = {}
        )
    )
  }
}

@Preview
@Composable
internal fun MoneyHomeScreenLiteWithoutProtectedCustomers() {
  PreviewWalletTheme {
    LiteMoneyHomeScreen(
      model =
        LiteMoneyHomeBodyModel(
          onSettings = {},
          buttonModel = MoneyHomeButtonsModel.SingleButtonModel(onSetUpBitkeyDevice = { }),
          protectedCustomers = immutableListOf(),
          onProtectedCustomerClick = {},
          onBuyOwnBitkeyClick = {},
          onAcceptInviteClick = {}
        )
    )
  }
}
