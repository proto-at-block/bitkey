package build.wallet.ui.app.moneyhome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.ui.app.moneyhome.card.MoneyHomeCard
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.ButtonContentsList
import build.wallet.ui.components.button.RowOfButtons
import build.wallet.ui.components.coachmark.CoachmarkPresenter
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.refresh.PullRefreshIndicator
import build.wallet.ui.components.refresh.pullRefresh
import build.wallet.ui.components.tabbar.Tab
import build.wallet.ui.components.tabbar.TabBar
import build.wallet.ui.components.toolbar.ToolbarAccessory
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.theme.WalletTheme

@Composable
fun MoneyHomeScreen(
  modifier: Modifier = Modifier,
  model: MoneyHomeBodyModel,
) {
  val listState = rememberLazyListState()
  var coachmarkOffset by remember { mutableStateOf(Offset(0f, 0f)) }

  Box(
    modifier = modifier.pullRefresh(
      refreshing = model.isRefreshing,
      onRefresh = model.onRefresh
    ).background(WalletTheme.colors.background)
  ) {
    // Display a coachmark if needed
    model.coachmark?.let { coachmarkModel ->
      CoachmarkPresenter(
        yOffset = coachmarkOffset.y,
        model = coachmarkModel
      )
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
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
            headline = "Bitkey",
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
              onClick = {
                model.onHideBalance()
                // dismiss the HiddenBalanceCoachmark coachmark if it's showing since you've interacted with the feature
                if (model.coachmark?.identifier ==
                  CoachmarkIdentifier.HiddenBalanceCoachmark
                ) {
                  model.coachmark.dismiss()
                }
              }
            ).onGloballyPositioned { layoutCoordinates ->
              if (model.coachmark?.identifier == CoachmarkIdentifier.HiddenBalanceCoachmark) {
                val positionInParent = layoutCoordinates.positionInParent()
                val size = layoutCoordinates.size
                coachmarkOffset = Offset(
                  0f,
                  positionInParent.y + size.height
                )
              }
            },
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
            modifier = Modifier
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

    if (model.tabs.isNotEmpty()) {
      TabBar(
        modifier = Modifier.align(Alignment.BottomCenter)
      ) {
        model.tabs.map {
          Tab(selected = it.selected, onClick = it.onSelected)
        }
      }
    }
  }
}

@Composable
fun LiteMoneyHomeScreen(
  modifier: Modifier = Modifier,
  model: LiteMoneyHomeBodyModel,
) {
  val listState = rememberLazyListState()
  Column(
    modifier = modifier
      .background(WalletTheme.colors.background)
  ) {
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
              headline = "Bitkey",
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
    is MoneyHomeButtonsModel.MoneyMovementButtonsModel -> {
      val buttonCount = model.buttons.size
      val spacing = if (buttonCount > 3) 20.dp else 40.dp

      CircularActions(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 16.dp)
          .padding(horizontal = 20.dp),
        buttonContentsList = ButtonContentsList(
          buttonContents = model.buttons.map {
            {
              IconButton(model = it)
            }
          }
        ),
        interButtonSpacing = spacing
      )
    }

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
  interButtonSpacing: Dp = 40.dp, // 20.dp of spacing on either side of each button
) {
  RowOfButtons(
    modifier = modifier,
    buttonContents = buttonContentsList,
    interButtonSpacing = interButtonSpacing
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
