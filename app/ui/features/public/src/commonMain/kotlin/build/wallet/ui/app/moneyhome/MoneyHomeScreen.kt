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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.statemachine.core.list.ListModel
import build.wallet.statemachine.home.full.HomeTab
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeButtonsModel
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.ui.app.moneyhome.card.MoneyHomeCard
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.ButtonContentsList
import build.wallet.ui.components.button.RowOfButtons
import build.wallet.ui.components.coachmark.CoachmarkPresenter
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.refresh.PullToRefreshBox
import build.wallet.ui.components.tabbar.Tab
import build.wallet.ui.components.tabbar.TabBar
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.toolbar.ToolbarAccessory
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun MoneyHomeScreen(
  modifier: Modifier = Modifier,
  model: MoneyHomeBodyModel,
) {
  val localDensity = LocalDensity.current
  val listState = rememberLazyListState()
  val collapseRangePx = with(localDensity) { MONEY_HOME_TITLE_COLLAPSE_RANGE.toPx() }
  var coachmarkOffset by remember {
    mutableStateOf(Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))
  }
  var tabBarHeightDp by remember {
    mutableStateOf(0.dp)
  }
  var moneyHomeYInRoot by remember {
    mutableStateOf(0f)
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
    CoachmarkIdentifier.PrivateWalletHomeCoachmark
  )
  val balanceCoachmarkIds = setOf(
    CoachmarkIdentifier.Bip177Coachmark
  )

  val collapseProgress by remember(listState, collapseRangePx) {
    derivedStateOf {
      if (collapseRangePx <= 0f) {
        0f
      } else {
        val scrollOffsetPx = if (listState.firstVisibleItemIndex > 0) {
          collapseRangePx
        } else {
          listState.firstVisibleItemScrollOffset.toFloat()
        }
        (scrollOffsetPx / collapseRangePx).coerceIn(0f, 1f)
      }
    }
  }

  PullToRefreshBox(
    refreshing = model.isRefreshing,
    onRefresh = model.onRefresh,
    modifier = modifier
      .background(WalletTheme.colors.background)
      .onGloballyPositioned { layoutCoordinates ->
        moneyHomeYInRoot = layoutCoordinates.positionInRoot().y
      }
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
      horizontalAlignment = Alignment.Start,
      state = listState
    ) {
      // Header
      item {
        MoneyHomeHeader()
      }

      // Balance + buttons
      item {
        val hasBalanceCoachmark = remember(model.coachmark) {
          model.coachmark?.identifier in balanceCoachmarkIds
        }
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          with(model.balanceModel) {
            HeroAmount(
              modifier = Modifier
                .clickable(
                  interactionSource = MutableInteractionSource(),
                  indication = null,
                  onClick = {
                    model.onHideBalance()
                  }
                )
                .thenIf(hasBalanceCoachmark) {
                  Modifier.onGloballyPositioned { layoutCoordinates ->
                    val positionInRoot = layoutCoordinates.positionInRoot()
                    val size = layoutCoordinates.size
                    coachmarkOffset = Offset(
                      0f,
                      positionInRoot.y - moneyHomeYInRoot + size.height
                    )
                  }
                },
              primaryAmount = AnnotatedString(primaryAmount),
              primaryAmountValue = primaryAmountValue,
              primaryAmountAnimationKey = primaryAmountAnimationKey,
              contextLine = secondaryAmount,
              hideBalance = model.hideBalance,
              centerContent = true,
              isLoading = isLoading,
              animateValueChanges = true
            )
          }
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
        MoneyHomeCard(
          modifier = Modifier
            .padding(horizontal = 20.dp),
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

      item {
        Spacer(Modifier.height(tabBarHeightDp))
      }
    }

    MoneyHomeOverlayToolbar(
      trailingToolbarAccessoryModel = model.trailingToolbarAccessoryModel,
      collapseProgress = collapseProgress
    )

    val hasCoachmark = remember(model.coachmark, coachmarkHeight) {
      model.coachmark?.identifier in tabBarCoachmarkIds && coachmarkHeight != null
    }
    val tabs = persistentListOf(
      HomeTab.MoneyHome(
        selected = true,
        onSelected = {}
      ),
      HomeTab.SecurityHub(
        selected = false,
        onSelected = model.onSecurityHubTabClick,
        badged = model.isSecurityHubBadged
      )
    )
    val selectedIndex = tabs.indexOfFirst { it.selected }.let {
        index ->
      if (index == -1) 0 else index
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
        },
      selectedIndex = selectedIndex,
      tabCount = tabs.size
    ) {
      MoneyHomeTabs(
        tabs = tabs
      )
    }
  }
}

@Composable
private fun MoneyHomeHeader() {
  // Reserve space for the fixed top toolbar.
  Spacer(Modifier.height(MONEY_HOME_TOOLBAR_RESERVED_HEIGHT + MONEY_HOME_CONTENT_TOP_PADDING))
}

@Composable
private fun BoxScope.MoneyHomeOverlayToolbar(
  trailingToolbarAccessoryModel: ToolbarAccessoryModel,
  collapseProgress: Float,
) {
  MoneyHomeCollapsibleToolbar(
    trailingAccessory = trailingToolbarAccessoryModel.withCircleBackground(),
    collapseProgress = collapseProgress,
    inlineTitle = null
  )
}

@Composable
private fun RowScope.MoneyHomeTabs(
  tabs: ImmutableList<HomeTab>,
) {
  tabs.forEachIndexed { index, tab ->
    Box(
      modifier = Modifier.weight(1f),
      contentAlignment = Alignment.Center
    ) {
      Tab(
        selected = tab.selected,
        onClick = tab.onSelected,
        icon = tab.icon,
        badged = tab.badged,
        modifier = Modifier.offset(x = if (index == 0) 3.dp else (-3).dp)
      )
    }
  }
}

@Composable
private fun BoxScope.MoneyHomeCollapsibleToolbar(
  trailingAccessory: ToolbarAccessoryModel,
  collapseProgress: Float,
  inlineTitle: String?,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(
        MONEY_HOME_TOOLBAR_TOP_PADDING +
          MONEY_HOME_TOOLBAR_HEIGHT +
          MONEY_HOME_TOOLBAR_BOTTOM_PADDING +
          MONEY_HOME_TOOLBAR_BOTTOM_GRADIENT_HEIGHT
      )
      .align(Alignment.TopCenter)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(MONEY_HOME_TOOLBAR_RESERVED_HEIGHT)
        .background(WalletTheme.colors.background)
    ) {
      Box(
        modifier = Modifier
          .padding(
            top = MONEY_HOME_TOOLBAR_TOP_PADDING,
            start = MONEY_HOME_HORIZONTAL_PADDING,
            end = MONEY_HOME_HORIZONTAL_PADDING
          )
          .fillMaxWidth()
          .height(MONEY_HOME_TOOLBAR_HEIGHT)
      ) {
        Toolbar(
          leadingContent = {
            MoneyHomeLeadingHeader()
          },
          trailingContent = {
            ToolbarAccessory(model = trailingAccessory)
          },
          showDesignSystemChrome = false
        )

        inlineTitle?.let { title ->
          Label(
            modifier = Modifier
              .fillMaxWidth()
              .padding(
                start = MONEY_HOME_INLINE_TITLE_START_WITH_LEADING,
                end = MONEY_HOME_INLINE_TITLE_END_PADDING
              )
              .align(Alignment.CenterStart)
              .alpha(collapseProgress),
            text = title,
            type = LabelType.Title2
          )
        }
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(MONEY_HOME_TOOLBAR_BOTTOM_GRADIENT_HEIGHT)
        .align(Alignment.BottomCenter)
        .background(
          brush =
            Brush.verticalGradient(
              colors =
                listOf(
                  WalletTheme.colors.background,
                  WalletTheme.colors.background.copy(alpha = 0.65f),
                  Color.Transparent
                )
            )
        )
    )
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
            verticalAlignment = Alignment.Top
          ) {
            MoneyHomeLeadingHeader()
            Spacer(Modifier.weight(1F))
            ToolbarAccessory(
              model = model.trailingToolbarAccessoryModel.withCircleBackground()
            )
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

private val MONEY_HOME_HORIZONTAL_PADDING = 20.dp
private val MONEY_HOME_TOOLBAR_TOP_PADDING = 8.dp
private val MONEY_HOME_TOOLBAR_HEIGHT = 48.dp
private val MONEY_HOME_TOOLBAR_BOTTOM_PADDING = 8.dp
private val MONEY_HOME_TOOLBAR_BOTTOM_GRADIENT_HEIGHT = 20.dp
private val MONEY_HOME_TOOLBAR_RESERVED_HEIGHT =
  MONEY_HOME_TOOLBAR_TOP_PADDING + MONEY_HOME_TOOLBAR_HEIGHT + MONEY_HOME_TOOLBAR_BOTTOM_PADDING
private val MONEY_HOME_CONTENT_TOP_PADDING = 24.dp
private val MONEY_HOME_INLINE_TITLE_START_WITH_LEADING = 56.dp
private val MONEY_HOME_INLINE_TITLE_END_PADDING = 56.dp
private val MONEY_HOME_TITLE_COLLAPSE_RANGE = 120.dp

@Composable
private fun MoneyHomeLeadingHeader() {
  Label(text = "Wallet", type = LabelType.Title2)
}

private fun ToolbarAccessoryModel.withCircleBackground(): ToolbarAccessoryModel {
  return when (this) {
    is ToolbarAccessoryModel.ButtonAccessory -> this
    is ToolbarAccessoryModel.IconAccessory -> copy(
      model = model.copy(
        iconModel = model.iconModel.copy(
          iconBackgroundType = IconBackgroundType.Circle(
            circleSize = IconSize.Regular,
            color = IconBackgroundType.Circle.CircleColor.SubtleBackground
          )
        )
      )
    )
  }
}

@Composable
private fun MoneyHomeButtons(model: MoneyHomeButtonsModel) {
  when (model) {
    is MoneyHomeButtonsModel.MoneyMovementButtonsModel -> {
      BoxWithConstraints(
        Modifier
          .fillMaxWidth()
          .padding(top = 16.dp)
      ) {
        val buttonCount = model.buttons.size
        // Divide the width of the screen into chunks for each button
        val chunkedWidth = maxWidth / buttonCount
        // Use 1/12th of the space in each chunk for padding on either side of the buttons
        val interButtonSpacing = chunkedWidth / 12
        // Button size is equal to the width of the chunk minus the padding on each side
        val buttonSize = chunkedWidth - (interButtonSpacing * 2)
        val buttons = if (buttonSize >= 80.dp) {
          model.buttons.map { it.withCircleSize(IconSize.Custom(80)) }
        } else {
          model.buttons
        }
        RowOfButtons(
          modifier = Modifier.fillMaxWidth(),
          buttonContents = ButtonContentsList(
            buttonContents = buttons.map {
              {
                IconButton(
                  modifier = Modifier.size(buttonSize),
                  model = it
                )
              }
            }
          ),
          interButtonSpacing = interButtonSpacing
        )
      }
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

private fun IconButtonModel.withCircleSize(circleSize: IconSize): IconButtonModel {
  val backgroundType = iconModel.iconBackgroundType
  return if (backgroundType is IconBackgroundType.Circle) {
    copy(
      iconModel = iconModel.copy(
        iconBackgroundType = backgroundType.copy(circleSize = circleSize)
      )
    )
  } else {
    this
  }
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
