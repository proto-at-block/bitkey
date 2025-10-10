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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.home.full.HomeTab
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.card.CardModel
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
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.theme.WalletTheme

@Composable
fun MoneyHomeScreen(
  modifier: Modifier = Modifier,
  model: MoneyHomeBodyModel,
) {
  val localDensity = LocalDensity.current
  val listState = rememberLazyListState()
  var coachmarkOffset by remember {
    mutableStateOf(Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
  }
  var tabBarHeightDp by remember {
    mutableStateOf(0.dp)
  }

  // Different coachmarks have different heights (e.g., Security Hub has no button,
  // Private Wallet has a button), so each needs its own height for correct positioning.
  var coachmarkHeights by remember {
    mutableStateOf(mapOf<CoachmarkIdentifier, Int>())
  }

  val coachmarkHeight = model.coachmark?.identifier?.let { identifier ->
    coachmarkHeights[identifier]
  }

  // Coachmarks that appear above the tab bar and need positioning
  val tabBarCoachmarkIds = setOf(
    CoachmarkIdentifier.SecurityHubHomeCoachmark,
    CoachmarkIdentifier.PrivateWalletHomeCoachmark
  )

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
        model = coachmarkModel,
        renderedSize = { size ->
          coachmarkModel.identifier.let { identifier ->
            coachmarkHeights = coachmarkHeights + (identifier to size.height)
          }
        }
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
        Spacer(Modifier.height(40.dp))
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
              }
            ),
            primaryAmount = AnnotatedString(primaryAmount),
            contextLine = secondaryAmount,
            hideBalance = model.hideBalance
          )
        }
        Spacer(Modifier.height(32.dp))
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
        val hasCoachmark = remember(model.coachmark, cardModel.content) {
          model.coachmark?.identifier == CoachmarkIdentifier.BalanceGraphCoachmark &&
            cardModel.content is CardModel.CardContent.BitcoinPrice
        }
        MoneyHomeCard(
          modifier = Modifier
            .padding(horizontal = 20.dp)
            .thenIf(hasCoachmark) {
              Modifier
                .onGloballyPositioned { layoutCoordinates ->
                  val positionInParent = layoutCoordinates.positionInParent()
                  val size = layoutCoordinates.size
                  coachmarkOffset = Offset(
                    0f,
                    positionInParent.y + size.height
                  )
                }
            },
          model = if (hasCoachmark) {
            cardModel.copy(onClick = {
              cardModel.onClick?.invoke()
              model.coachmark?.dismiss?.invoke()
            })
          } else {
            cardModel
          }
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

      item {
        Spacer(Modifier.height(tabBarHeightDp))
      }
    }

    PullRefreshIndicator(
      modifier = Modifier.align(Alignment.TopCenter),
      refreshing = model.isRefreshing,
      onRefresh = model.onRefresh
    )

    val hasCoachmark = remember(model.coachmark, coachmarkHeight) {
      model.coachmark?.identifier in tabBarCoachmarkIds && coachmarkHeight != null
    }
    TabBar(
      modifier = Modifier.align(Alignment.BottomCenter)
        .onGloballyPositioned { layoutCoordinates ->
          tabBarHeightDp = with(localDensity) { layoutCoordinates.size.height.toDp() + 36.dp }
          if (hasCoachmark) {
            val positionInParent = layoutCoordinates.positionInParent()
            coachmarkOffset = coachmarkHeight?.let { height ->
              Offset(
                0f,
                positionInParent.y - height
              )
            } ?: Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
          }
        }
    ) {
      listOf(
        HomeTab.MoneyHome(
          selected = true,
          onSelected = {}
        ),
        HomeTab.SecurityHub(
          selected = false,
          onSelected = model.onSecurityHubTabClick,
          badged = model.isSecurityHubBadged
        )
      ).map {
        Tab(
          selected = it.selected,
          onClick = it.onSelected,
          icon = it.icon,
          badged = it.badged
        )
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
