package bitkey.ui.screens.securityhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bitkey.securitycenter.SecurityAction
import bitkey.securitycenter.SecurityActionCategory
import bitkey.securitycenter.SecurityActionRecommendation
import bitkey.securitycenter.SecurityActionsService
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.home.full.HomeTab
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.refresh.PullRefreshIndicator
import build.wallet.ui.components.refresh.pullRefresh
import build.wallet.ui.components.tabbar.Tab
import build.wallet.ui.components.tabbar.TabBar
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.data.DataGroup
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.status.StatusBannerModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class SecurityHubScreen(
  val homeStatusBannerModel: StatusBannerModel?,
  // TODO W-11082 Update tabs interface for supporting the new tab bar
  val tabs: List<HomeTab>,
) : Screen

@BitkeyInject(ActivityScope::class)
class SecurityHubPresenter(
  private val securityActionsService: SecurityActionsService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
) : ScreenPresenter<SecurityHubScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: SecurityHubScreen,
  ): ScreenModel {
    var securityActions by remember { mutableStateOf(emptyList<SecurityAction>()) }
    var recoveryActions by remember { mutableStateOf(emptyList<SecurityAction>()) }
    var recommendations by remember { mutableStateOf(emptyList<SecurityActionRecommendation>()) }

    var isRefreshing by remember { mutableStateOf(true) }

    if (isRefreshing) {
      LaunchedEffect("update-actions-and-recommendations") {
        withMinimumDelay(minimumLoadingDuration.value) {
          coroutineScope {
            launch {
              securityActions = securityActionsService.getActions(SecurityActionCategory.SECURITY)
            }
            launch {
              recoveryActions = securityActionsService.getActions(SecurityActionCategory.RECOVERY)
            }
            launch {
              recommendations = securityActionsService.getRecommendations()
            }
          }
        }
        isRefreshing = false
      }
    }

    return SecurityHubBodyModel(
      isRefreshing = isRefreshing,
      onRefresh = { isRefreshing = true },
      recommendations = recommendations.toImmutableList(),
      tabs = screen.tabs,
      securityActions = securityActions,
      recoveryActions = recoveryActions
    ).asRootFullScreen(statusBannerModel = screen.homeStatusBannerModel)
  }
}

data class SecurityHubBodyModel(
  val isRefreshing: Boolean,
  val onRefresh: () -> Unit,
  val recommendations: List<SecurityActionRecommendation>,
  val tabs: List<HomeTab>,
  val securityActions: List<SecurityAction> = emptyList(),
  val recoveryActions: List<SecurityAction> = emptyList(),
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = EventTrackerScreenInfo(
    eventTrackerScreenId = SecurityHubEventTrackerScreenId.SECURITY_HUB_SCREEN,
    eventTrackerShouldTrack = false
  ),
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    val localDensity = LocalDensity.current
    var tabBarHeightDp by remember {
      mutableStateOf(0.dp)
    }
    Box(
      modifier = modifier
        .pullRefresh(
          refreshing = isRefreshing,
          onRefresh = onRefresh
        )
        .fillMaxSize()
        .navigationBarsPadding()
        .background(WalletTheme.colors.background)
    ) {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
      ) {
        Column(
          modifier = Modifier.fillMaxWidth()
            .background(color = WalletTheme.colors.subtleBackground)
            .padding(horizontal = 20.dp)
            .statusBarsPadding()
        ) {
          Label(
            model = LabelModel.StringModel("Security hub"),
            style = WalletTheme.labelStyle(LabelType.Title1, textColor = WalletTheme.colors.foreground)
          )

          // TODO W-11071 Update the recommendations to the design spec
          if (recommendations.isNotEmpty()) {
            DataGroup(
              rows = FormMainContentModel.DataList(
                items = recommendations.map {
                  FormMainContentModel.DataList.Data(title = "Recommendation", sideText = "")
                }.toImmutableList()
              )
            )
          }
          Spacer(modifier = Modifier.height(32.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
          if (securityActions.isNotEmpty()) {
            HubActionSection(
              sectionTitle = "Security",
              actions = securityActions.toImmutableList()
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          if (recoveryActions.isNotEmpty()) {
            HubActionSection(
              sectionTitle = "Recovery",
              actions = recoveryActions.toImmutableList()
            )
          }
          Spacer(Modifier.height(tabBarHeightDp))
        }
      }

      PullRefreshIndicator(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
        refreshing = isRefreshing,
        onRefresh = onRefresh
      )

      TabBar(
        modifier = Modifier.align(Alignment.BottomCenter)
          .onGloballyPositioned {
            tabBarHeightDp = with(localDensity) { it.size.height.toDp() + 36.dp }
          }
      ) {
        tabs.map {
          Tab(selected = it.selected, onClick = it.onSelected)
        }
      }
    }
  }
}

@Composable
private fun HubActionSection(
  sectionTitle: String,
  actions: ImmutableList<SecurityAction>,
) {
  Column {
    Label(
      model = LabelModel.StringModel(sectionTitle),
      style = WalletTheme.labelStyle(
        LabelType.Title2,
        textColor = WalletTheme.colors.foreground
      )
    )

    Spacer(modifier = Modifier.height(10.dp))

    VerticalGrid(
      columns = 2,
      size = actions.size,
      verticalSpacing = 10.dp,
      horizontalSpacing = 10.dp
    ) { index ->
      ActionTile(
        action = actions[index]
      )
    }
  }
}

@Composable
private fun VerticalGrid(
  modifier: Modifier = Modifier,
  columns: Int,
  size: Int,
  verticalSpacing: Dp,
  horizontalSpacing: Dp,
  content: @Composable (Int) -> Unit,
) {
  Column(modifier = modifier) {
    val rows by remember(size, columns) {
      var amount = (size / columns)
      if (size % columns > 0) {
        amount += 1
      }
      mutableStateOf(amount)
    }

    for (rowIndex in 0 until rows) {
      val firstIndex = rowIndex * columns

      Row {
        for (columnIndex in 0 until columns) {
          val index = firstIndex + columnIndex
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
          ) {
            if (index < size) {
              content(index)
            }
          }
          if (columnIndex < columns - 1) {
            Spacer(Modifier.width(horizontalSpacing))
          }
        }
      }
      if (rowIndex < rows - 1) {
        Spacer(Modifier.height(verticalSpacing))
      }
    }
  }
}

@Composable
private fun ActionTile(
  modifier: Modifier = Modifier,
  action: SecurityAction,
) {
  // TODO W-11068 Update the action tile with appropriate Text, Icon and color based on the action
  Box(
    modifier = modifier.fillMaxWidth()
      .height(116.dp)
      .scalingClickable {
        // TODO W-11068 Navigate to the action details screen
      }
      .background(
        color = WalletTheme.colors.subtleBackground,
        shape = RoundedCornerShape(16.dp)
      )
  ) {
    Row(
      modifier = Modifier.fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = CenterVertically,
      horizontalArrangement = SpaceBetween
    ) {
      Icon(
        icon = Icon.SmallIconKey,
        size = IconSize.Small
      )

      Box(
        modifier = Modifier.background(
          color = if (action.getRecommendations().isEmpty()) {
            Color.Green
          } else {
            Color.Red
          },
          shape = CircleShape
        ).size(10.dp)
      )
    }

    Label(
      modifier = Modifier.align(Alignment.BottomStart)
        .padding(12.dp),
      text = "Action",
      style = WalletTheme.labelStyle(LabelType.Body2Medium)
    )
  }
}
