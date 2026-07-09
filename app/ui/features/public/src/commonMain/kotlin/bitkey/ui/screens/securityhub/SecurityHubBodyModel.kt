@file:Suppress("TooManyFunctions")

package bitkey.ui.screens.securityhub

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bitkey.securitycenter.*
import bitkey.securitycenter.SecurityActionRecommendation.*
import bitkey.securitycenter.SecurityActionType.*
import bitkey.ui.Snapshot
import bitkey.ui.SnapshotHost
import bitkey.ui.features_public.generated.resources.*
import build.wallet.Progress
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.compose.collections.immutableListOf
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.home.full.HomeTab
import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryCardModel
import build.wallet.statemachine.recovery.hardware.fingerprintreset.FingerprintResetCardModel
import build.wallet.ui.app.moneyhome.card.NewCard
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.tabbar.Tab
import build.wallet.ui.components.tabbar.TabBar
import build.wallet.ui.components.toolbar.ToolbarAccessory
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

private val DestructiveRed = Color(0xffca0000)
private val WarningOrange = Color(0xffbf46e38)
private val SuccessGreen = Color(0xff3aba5a)
private val DisabledGrey = Color(0xffc6c6c6)
private const val ALL_SET_REVEAL_ACTIVATION_THRESHOLD_FRACTION = 0.6f
private const val ALL_SET_REVEAL_MIN_ACTIVATION_THRESHOLD_PX = 36f
private const val ALL_SET_REVEAL_MAX_ACTIVATION_THRESHOLD_PX = 72f
private const val ALL_SET_REVEAL_MIN_RESISTANCE_FACTOR = 0.56f
private const val ALL_SET_REVEAL_MAX_RESISTANCE_FACTOR = 0.88f

internal fun securityHubToolbarAccessoryModel(onClick: () -> Unit = {}) =
  ToolbarAccessoryModel.IconAccessory(
    model = IconButtonModel(
      iconModel = IconModel(
        icon = Icon.EllipsisHorizontal,
        iconSize = IconSize.HeaderToolbar,
        iconBackgroundType = IconBackgroundType.Circle(
          circleSize = IconSize.Regular,
          color = IconBackgroundType.Circle.CircleColor.SubtleBackground
        ),
        iconTint = IconTint.Foreground
      ),
      testTag = "security-hub-settings",
      onClick = StandardClick(onClick)
    )
  )

data class SecurityHubBodyModel(
  val isOffline: Boolean = false,
  val atRiskRecommendations: ImmutableList<SecurityActionRecommendation>,
  val recommendations: ImmutableList<SecurityActionRecommendation>,
  val cardsModel: CardListModel,
  val showAllSetState: Boolean = false,
  val foregroundSessionGeneration: Int = 0,
  val securityActions: List<SecurityAction> = emptyList(),
  val recoveryActions: List<SecurityAction> = emptyList(),
  val autoHideAllSetPillAfterDelay: Boolean = false,
  val hideAllSetPillOnEntry: Boolean = false,
  val onAllSetPillAutoHidden: () -> Boolean = { false },
  val onRecommendationClick: (SecurityActionRecommendation) -> Unit,
  val onSecurityActionClick: (SecurityAction) -> Unit,
  val onHomeTabClick: () -> Unit,
  val trailingToolbarAccessoryModel: ToolbarAccessoryModel = securityHubToolbarAccessoryModel(),
  val haptics: Haptics? = null,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = EventTrackerScreenInfo(
    eventTrackerScreenId = SecurityHubEventTrackerScreenId.SECURITY_HUB_SCREEN
  ),
) : BodyModel() {
  @Suppress("CyclomaticComplexMethod")
  @Composable
  override fun render(modifier: Modifier) {
    val localDensity = LocalDensity.current
    val scrollState = rememberScrollState()
    val contentTopPaddingDp = SECURITY_HUB_TOOLBAR_RESERVED_HEIGHT + SECURITY_HUB_CONTENT_TOP_PADDING
    val shouldRenderAllSetPill =
      showAllSetState &&
        !isOffline
    var allSetHeaderHeightPx by remember { mutableIntStateOf(0) }
    var allSetBaseContentHeightPx by remember { mutableIntStateOf(0) }
    var hasAppliedInitialAllSetScroll by remember(hideAllSetPillOnEntry, shouldRenderAllSetPill) {
      mutableStateOf(!hideAllSetPillOnEntry)
    }
    var hasHandledAutoHideAllSetPill by remember(
      foregroundSessionGeneration,
      autoHideAllSetPillAfterDelay,
      shouldRenderAllSetPill
    ) {
      mutableStateOf(false)
    }
    var viewportHeightPx by remember {
      mutableIntStateOf(0)
    }
    var tabBarHeightDp by remember {
      mutableStateOf(0.dp)
    }

    Box(
      modifier = modifier.fillMaxSize()
        .onGloballyPositioned { coordinates ->
          viewportHeightPx = coordinates.size.height
        }
        .background(
          color = screenBackgroundColor(isOffline)
        )
    ) {
      val contentTopPaddingPx = with(localDensity) { contentTopPaddingDp.roundToPx() }
      val allSetScrollTargetPx = minOf(allSetHeaderHeightPx, scrollState.maxValue)
      val allSetRevealActivationThresholdPx = remember(allSetScrollTargetPx) {
        calculateAllSetPillRevealActivationThresholdPx(allSetScrollTargetPx)
      }
      var remainingAllSetRevealActivationPullPx by remember(
        shouldRenderAllSetPill,
        allSetScrollTargetPx
      ) {
        mutableFloatStateOf(allSetRevealActivationThresholdPx)
      }
      val allSetRevealResistanceConnection = remember(
        shouldRenderAllSetPill,
        allSetScrollTargetPx,
        allSetRevealActivationThresholdPx
      ) {
        object : NestedScrollConnection {
          override fun onPreScroll(
            available: Offset,
            source: NestedScrollSource,
          ): Offset {
            if (
              source != NestedScrollSource.UserInput ||
              !shouldApplyAllSetPillRevealResistance(
                shouldRenderAllSetPill = shouldRenderAllSetPill,
                allSetScrollTargetPx = allSetScrollTargetPx,
                scrollValue = scrollState.value,
                availableScrollDeltaY = available.y
              )
            ) {
              return Offset.Zero
            }

            val revealResistanceResult = calculateAllSetPillRevealResistanceResult(
              availableScrollDeltaY = available.y,
              remainingActivationPullPx = remainingAllSetRevealActivationPullPx,
              scrollValue = scrollState.value,
              allSetScrollTargetPx = allSetScrollTargetPx
            )
            remainingAllSetRevealActivationPullPx =
              revealResistanceResult.remainingActivationPullPx

            return Offset(
              x = 0f,
              y = revealResistanceResult.consumedScrollY
            )
          }
        }
      }
      var hasSeenHiddenAllSetPill by remember(shouldRenderAllSetPill, allSetScrollTargetPx) {
        mutableStateOf(false)
      }
      val allSetScrollAllowanceDp = remember(
        shouldRenderAllSetPill,
        viewportHeightPx,
        contentTopPaddingPx,
        allSetHeaderHeightPx,
        allSetBaseContentHeightPx
      ) {
        if (
          !shouldRenderAllSetPill ||
          viewportHeightPx == 0 ||
          allSetHeaderHeightPx == 0
        ) {
          0.dp
        } else {
          with(localDensity) {
            (
              viewportHeightPx + allSetHeaderHeightPx - contentTopPaddingPx - allSetBaseContentHeightPx
            ).coerceAtLeast(0).toDp()
          }
        }
      }

      LaunchedEffect(
        foregroundSessionGeneration,
        shouldRenderAllSetPill,
        hideAllSetPillOnEntry,
        autoHideAllSetPillAfterDelay,
        allSetScrollTargetPx
      ) {
        if (!shouldRenderAllSetPill || allSetScrollTargetPx == 0) return@LaunchedEffect

        if (hideAllSetPillOnEntry && !hasAppliedInitialAllSetScroll) {
          if (shouldScrollToHideAllSetPillOnEntry(scrollState.value, allSetScrollTargetPx)) {
            scrollState.scrollTo(allSetScrollTargetPx)
          }
          hasAppliedInitialAllSetScroll = true
          return@LaunchedEffect
        }

        if (autoHideAllSetPillAfterDelay && !hasHandledAutoHideAllSetPill) {
          if (isAllSetPillHidden(scrollState.value, allSetScrollTargetPx)) {
            if (onAllSetPillAutoHidden()) {
              hasHandledAutoHideAllSetPill = true
            }
            return@LaunchedEffect
          }

          delay(4.seconds)
          if (shouldAnimateAllSetPillAutoHide(scrollState.value, allSetScrollTargetPx)) {
            scrollState.animateScrollTo(
              value = allSetScrollTargetPx,
              animationSpec = tween(
                durationMillis = 650,
                easing = LinearOutSlowInEasing
              )
            )
          }
          if (scrollState.value >= allSetScrollTargetPx && onAllSetPillAutoHidden()) {
            hasHandledAutoHideAllSetPill = true
          }
        }
      }

      LaunchedEffect(
        shouldRenderAllSetPill,
        allSetScrollTargetPx,
        scrollState.value
      ) {
        if (isAllSetPillHidden(scrollState.value, allSetScrollTargetPx)) {
          remainingAllSetRevealActivationPullPx = allSetRevealActivationThresholdPx
          hasSeenHiddenAllSetPill = true
          return@LaunchedEffect
        }

        if (
          shouldTriggerAllSetPillRevealHaptic(
            shouldRenderAllSetPill = shouldRenderAllSetPill,
            allSetScrollTargetPx = allSetScrollTargetPx,
            scrollValue = scrollState.value,
            hasSeenHiddenAllSetPill = hasSeenHiddenAllSetPill
          )
        ) {
          hasSeenHiddenAllSetPill = false
          haptics?.vibrate(HapticsEffect.Selection)
        }
      }

      // Small background to cover the bottom of the screen so the overscroll on iOS is the
      // background color
      Box(
        modifier = Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .height(300.dp)
          .background(WalletTheme.colors.background)
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .thenIf(
            hideAllSetPillOnEntry &&
              !hasAppliedInitialAllSetScroll &&
              (allSetScrollTargetPx == 0 || scrollState.value < allSetScrollTargetPx)
          ) {
            Modifier.alpha(0f)
          }
          .thenIf(shouldRenderAllSetPill) {
            Modifier.nestedScroll(allSetRevealResistanceConnection)
          }
          .verticalScroll(scrollState)
          .padding(top = contentTopPaddingDp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .thenIf(shouldRenderAllSetPill) {
              Modifier.onGloballyPositioned { coordinates ->
                allSetBaseContentHeightPx = coordinates.size.height
              }
            }
        ) {
          SecurityHubHeaderSection(
            modifier = Modifier.thenIf(shouldRenderAllSetPill) {
              Modifier.onGloballyPositioned { coordinates ->
                allSetHeaderHeightPx = coordinates.size.height
              }
            },
            isOffline = isOffline,
            atRiskRecommendations = atRiskRecommendations,
            recommendations = recommendations,
            cardsModel = cardsModel,
            showAllSetState = showAllSetState,
            onRecommendationClick = onRecommendationClick
          )

          SecurityHubActionsSection(
            securityActions = securityActions.toImmutableList(),
            recoveryActions = recoveryActions.toImmutableList(),
            onSecurityActionClick = onSecurityActionClick,
            reduceTopSpacingForAllSetPill = shouldRenderAllSetPill,
            tabBarHeightDp = tabBarHeightDp
          )
        }

        if (shouldRenderAllSetPill && allSetScrollAllowanceDp > 0.dp) {
          Spacer(modifier = Modifier.height(allSetScrollAllowanceDp))
        }
      }

      SecurityHubTopToolbar(
        modifier = Modifier.align(Alignment.TopCenter),
        trailingToolbarAccessoryModel = trailingToolbarAccessoryModel
      )

      val tabs = listOf(
        HomeTab.MoneyHome(
          selected = false,
          onSelected = onHomeTabClick
        ),
        HomeTab.SecurityHub(
          selected = true,
          onSelected = {},
          badged = false
        )
      )
      val selectedIndex = tabs.indexOfFirst { it.selected }.let {
          index ->
        if (index == -1) 0 else index
      }

      TabBar(
        modifier = Modifier.align(Alignment.BottomCenter)
          .onGloballyPositioned {
            tabBarHeightDp = with(localDensity) { it.size.height.toDp() + 36.dp }
          },
        selectedIndex = selectedIndex,
        tabCount = tabs.size
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
              modifier = Modifier.offset(x = if (index == 0) 3.dp else (-3).dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SecurityHubHeaderSection(
  modifier: Modifier = Modifier,
  isOffline: Boolean,
  atRiskRecommendations: ImmutableList<SecurityActionRecommendation>,
  recommendations: ImmutableList<SecurityActionRecommendation>,
  cardsModel: CardListModel,
  showAllSetState: Boolean,
  onRecommendationClick: (SecurityActionRecommendation) -> Unit,
) {
  Column(
    modifier = modifier.fillMaxWidth()
      .background(color = screenBackgroundColor(isOffline))
      .padding(horizontal = 20.dp)
  ) {
    if (!isOffline) {
      if (atRiskRecommendations.isNotEmpty()) {
        RecommendationList(
          modifier = Modifier.fillMaxWidth(),
          recommendations = atRiskRecommendations,
          onRecommendationClick = onRecommendationClick,
          type = RecommendationType.Critical
        )

        Spacer(modifier = Modifier.height(12.dp))
      }

      // TODO W-11412 filter this in the service, not in the UI
      if (atRiskRecommendations.isEmpty()) {
        cardsModel.cards.forEach {
          NewCard(model = it)
          Spacer(modifier = Modifier.height(8.dp))
        }

        if (cardsModel.cards.isNotEmpty()) {
          Spacer(modifier = Modifier.height(12.dp))
        }

        when {
          recommendations.isNotEmpty() -> {
            RecommendationList(
              modifier = Modifier.fillMaxWidth(),
              recommendations = recommendations,
              onRecommendationClick = onRecommendationClick,
              type = RecommendationType.Recommended
            )
          }
          showAllSetState -> {
            RecommendationList(
              modifier = Modifier.fillMaxWidth(),
              recommendations = immutableListOf(),
              onRecommendationClick = onRecommendationClick,
              type = RecommendationType.Recommended
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun SecurityHubTopToolbar(
  modifier: Modifier = Modifier,
  trailingToolbarAccessoryModel: ToolbarAccessoryModel,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(
        SECURITY_HUB_TOP_PADDING +
          SECURITY_HUB_TOOLBAR_HEIGHT +
          SECURITY_HUB_TOOLBAR_BOTTOM_PADDING +
          SECURITY_HUB_TOOLBAR_BOTTOM_GRADIENT_HEIGHT
      )
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(SECURITY_HUB_TOOLBAR_RESERVED_HEIGHT)
        .background(WalletTheme.colors.background)
    ) {
      Box(
        modifier = Modifier
          .padding(
            top = SECURITY_HUB_TOP_PADDING,
            start = SECURITY_HUB_HORIZONTAL_PADDING,
            end = SECURITY_HUB_HORIZONTAL_PADDING
          )
          .fillMaxWidth()
          .height(SECURITY_HUB_TOOLBAR_HEIGHT)
      ) {
        Label(
          modifier = Modifier.align(Alignment.CenterStart),
          text = "Security Hub",
          style = WalletTheme.labelStyle(
            LabelType.Title2,
            textColor = WalletTheme.colors.foreground
          )
        )

        Box(
          modifier = Modifier.align(Alignment.CenterEnd)
        ) {
          ToolbarAccessory(model = trailingToolbarAccessoryModel)
        }
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(SECURITY_HUB_TOOLBAR_BOTTOM_GRADIENT_HEIGHT)
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
private fun SecurityHubActionsSection(
  securityActions: ImmutableList<SecurityAction>,
  recoveryActions: ImmutableList<SecurityAction>,
  onSecurityActionClick: (SecurityAction) -> Unit,
  reduceTopSpacingForAllSetPill: Boolean,
  tabBarHeightDp: Dp,
) {
  if (securityActions.isEmpty() && recoveryActions.isEmpty()) return

  Column(
    modifier = Modifier.background(WalletTheme.colors.background)
      .padding(horizontal = 20.dp)
  ) {
    Spacer(
      modifier = Modifier.height(
        if (reduceTopSpacingForAllSetPill) 8.dp else 32.dp
      )
    )

    if (securityActions.isNotEmpty()) {
      HubActionSection(
        sectionTitle = "Security",
        actions = securityActions,
        onTileClick = onSecurityActionClick
      )
    }

    Spacer(modifier = Modifier.height(20.dp))

    if (recoveryActions.isNotEmpty()) {
      HubActionSection(
        sectionTitle = "Recovery",
        actions = recoveryActions,
        onTileClick = onSecurityActionClick
      )
    }
    Spacer(Modifier.height(tabBarHeightDp))
  }
}

@Composable
@ReadOnlyComposable
private fun screenBackgroundColor(isOffline: Boolean): Color =
  if (isOffline) {
    WalletTheme.colors.background
  } else {
    WalletTheme.colors.background
  }

/**
 * Represents the type of recommendation. Used to determine the color of the recommendation
 * indicator.
 */
private sealed interface RecommendationType {
  /**
   * Indicates that the recommendation is critical and requires immediate action, associated with
   * funds loss
   */
  object Critical : RecommendationType

  /**
   * Indicates that the recommendation is recommended but not critical.
   */
  object Recommended : RecommendationType
}

@Composable
private fun RecommendationList(
  modifier: Modifier = Modifier,
  type: RecommendationType,
  recommendations: ImmutableList<SecurityActionRecommendation>,
  onRecommendationClick: (SecurityActionRecommendation) -> Unit,
) {
  Column(
    modifier = modifier
  ) {
    RecommendationHeader(
      modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
      numberOfRecommendations = recommendations.size,
      type = type
    )
    if (recommendations.isNotEmpty()) {
      recommendations.mapIndexed { index, recommendation ->
        Column(
          modifier = Modifier
        ) {
          RecommendationRow(
            recommendation = recommendation,
            recommendationIndex = index + 1,
            onRecommendationClick = onRecommendationClick
          )
          Divider(
            modifier = Modifier.fillMaxWidth(),
            color = WalletTheme.colors.subtleBackground
          )
        }
      }
    }
  }
}

@Composable
private fun RecommendationHeader(
  modifier: Modifier = Modifier,
  numberOfRecommendations: Int,
  type: RecommendationType,
) {
  val text = when (type) {
    RecommendationType.Critical -> "Wallet at risk"
    RecommendationType.Recommended -> when (numberOfRecommendations) {
      0 -> "You're all set"
      1 -> "1 recommended action"
      else -> "$numberOfRecommendations recommended actions"
    }
  }
  val shouldRenderAllSetPill =
    type == RecommendationType.Recommended &&
      numberOfRecommendations == 0

  Box(modifier = modifier) {
    if (shouldRenderAllSetPill) {
      Row(
        modifier = Modifier
          .background(
            color = WalletTheme.colors.subtleBackground,
            shape = RoundedCornerShape(100.dp)
          )
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = CenterVertically
      ) {
        RecommendationStatusDot(numberOfRecommendations, type)
        Spacer(modifier = Modifier.width(12.dp))
        Label(
          text = text,
          type = LabelType.Body3Mono,
          color = WalletTheme.colors.foreground60
        )
      }
    } else {
      Row(verticalAlignment = CenterVertically) {
        RecommendationStatusDot(numberOfRecommendations, type)
        Spacer(modifier = Modifier.width(12.dp))
        Label(
          text = text,
          type = LabelType.Body3Mono,
          treatment = LabelTreatment.Secondary
        )
      }
    }
  }
}

@Composable
fun RecommendationRow(
  recommendation: SecurityActionRecommendation,
  recommendationIndex: Int,
  onRecommendationClick: (SecurityActionRecommendation) -> Unit,
) {
  Row(
    modifier = Modifier.padding(vertical = 16.dp)
      .scalingClickable {
        onRecommendationClick(recommendation)
      },
    verticalAlignment = CenterVertically
  ) {
    Label(
      text = "[$recommendationIndex]",
      type = LabelType.Body3Mono,
      color = WalletTheme.colors.foreground
    )
    Spacer(modifier = Modifier.width(12.dp))
    Label(
      modifier = Modifier.weight(1.0f),
      text = stringResource(recommendation.title()),
      color = WalletTheme.colors.foreground,
      type = LabelType.Body3Mono,
      overflow = TextOverflow.Ellipsis
    )
    Icon(
      icon = Icon.CaretRight,
      size = IconSize.Accessory,
      color = WalletTheme.colors.foreground30
    )
  }
}

@Composable
private fun RecommendationStatusDot(
  numberOfRecommendations: Int,
  type: RecommendationType,
) {
  val color = if (numberOfRecommendations == 0) {
    SuccessGreen
  } else {
    when (type) {
      RecommendationType.Critical -> DestructiveRed
      RecommendationType.Recommended -> WarningOrange
    }
  }

  Box(
    modifier = Modifier
      .size(10.dp)
      .background(color = color, shape = CircleShape)
  )
}

internal fun isAllSetPillHidden(
  scrollValue: Int,
  allSetScrollTargetPx: Int,
): Boolean = allSetScrollTargetPx > 0 && scrollValue >= allSetScrollTargetPx

internal fun shouldScrollToHideAllSetPillOnEntry(
  scrollValue: Int,
  allSetScrollTargetPx: Int,
): Boolean = allSetScrollTargetPx > 0 && scrollValue < allSetScrollTargetPx

internal fun calculateAllSetPillRevealActivationThresholdPx(
  allSetScrollTargetPx: Int,
): Float =
  (allSetScrollTargetPx * ALL_SET_REVEAL_ACTIVATION_THRESHOLD_FRACTION)
    .coerceIn(
      ALL_SET_REVEAL_MIN_ACTIVATION_THRESHOLD_PX,
      ALL_SET_REVEAL_MAX_ACTIVATION_THRESHOLD_PX
    )

internal fun shouldApplyAllSetPillRevealResistance(
  shouldRenderAllSetPill: Boolean,
  allSetScrollTargetPx: Int,
  scrollValue: Int,
  availableScrollDeltaY: Float,
): Boolean =
  shouldRenderAllSetPill &&
    allSetScrollTargetPx > 0 &&
    scrollValue in 1..allSetScrollTargetPx &&
    availableScrollDeltaY > 0f

internal data class AllSetPillRevealResistanceResult(
  val consumedScrollY: Float,
  val remainingActivationPullPx: Float,
)

internal fun calculateAllSetPillRevealResistanceResult(
  availableScrollDeltaY: Float,
  remainingActivationPullPx: Float,
  scrollValue: Int,
  allSetScrollTargetPx: Int,
): AllSetPillRevealResistanceResult {
  val activationConsumption = minOf(
    availableScrollDeltaY.coerceAtLeast(0f),
    remainingActivationPullPx.coerceAtLeast(0f)
  )
  val remainingScrollDeltaY = availableScrollDeltaY - activationConsumption
  val frictionConsumption = if (remainingScrollDeltaY > 0f) {
    calculateAllSetPillRevealResistanceConsumption(
      availableScrollDeltaY = remainingScrollDeltaY,
      scrollValue = scrollValue,
      allSetScrollTargetPx = allSetScrollTargetPx
    )
  } else {
    0f
  }

  return AllSetPillRevealResistanceResult(
    consumedScrollY = activationConsumption + frictionConsumption,
    remainingActivationPullPx = (remainingActivationPullPx - activationConsumption).coerceAtLeast(0f)
  )
}

internal fun calculateAllSetPillRevealResistanceConsumption(
  availableScrollDeltaY: Float,
  scrollValue: Int,
  allSetScrollTargetPx: Int,
): Float =
  availableScrollDeltaY * calculateAllSetPillRevealResistanceFactor(
    scrollValue = scrollValue,
    allSetScrollTargetPx = allSetScrollTargetPx
  )

internal fun calculateAllSetPillRevealResistanceFactor(
  scrollValue: Int,
  allSetScrollTargetPx: Int,
): Float {
  if (allSetScrollTargetPx <= 0) return ALL_SET_REVEAL_MIN_RESISTANCE_FACTOR

  val hiddenProgress =
    (scrollValue.toFloat() / allSetScrollTargetPx.toFloat()).coerceIn(0f, 1f)

  return ALL_SET_REVEAL_MIN_RESISTANCE_FACTOR +
    (
      ALL_SET_REVEAL_MAX_RESISTANCE_FACTOR -
        ALL_SET_REVEAL_MIN_RESISTANCE_FACTOR
    ) * hiddenProgress
}

internal fun shouldAnimateAllSetPillAutoHide(
  scrollValue: Int,
  allSetScrollTargetPx: Int,
): Boolean = allSetScrollTargetPx > 0 && scrollValue < allSetScrollTargetPx

internal fun shouldTriggerAllSetPillRevealHaptic(
  shouldRenderAllSetPill: Boolean,
  allSetScrollTargetPx: Int,
  scrollValue: Int,
  hasSeenHiddenAllSetPill: Boolean,
): Boolean =
  shouldRenderAllSetPill &&
    allSetScrollTargetPx > 0 &&
    hasSeenHiddenAllSetPill &&
    scrollValue == 0

@Composable
private fun HubActionSection(
  sectionTitle: String,
  actions: ImmutableList<SecurityAction>,
  onTileClick: (SecurityAction) -> Unit,
) {
  Column {
    Label(
      model = LabelModel.StringModel(sectionTitle),
      type = LabelType.Body3Mono,
      treatment = LabelTreatment.Secondary
    )

    Spacer(modifier = Modifier.height(10.dp))

    VerticalGrid(
      columns = 2,
      size = actions.size,
      verticalSpacing = 10.dp,
      horizontalSpacing = 10.dp
    ) { index ->
      ActionTile(
        action = actions[index],
        onClick = onTileClick
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
  onClick: (SecurityAction) -> Unit,
) {
  Box(
    modifier = modifier.fillMaxWidth()
      .height(116.dp)
      .thenIf(action.state() != SecurityActionState.Disabled) {
        Modifier.scalingClickable {
          onClick(action)
        }
      }
      .background(
        color = WalletTheme.colors.secondary,
        shape = RoundedCornerShape(8.dp)
      )
      .thenIf(action.state() == SecurityActionState.Disabled) {
        Modifier.alpha(0.3f)
      }
  ) {
    Row(
      modifier = Modifier.fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = SpaceBetween
    ) {
      Icon(
        icon = action.icon(),
        size = IconSize.Regular
      )

      Box(
        modifier = Modifier
          .offset(y = 4.dp)
          .background(
            color = action.statusColor(),
            shape = CircleShape
          )
          .size(10.dp)
      )
    }

    Label(
      modifier = Modifier.align(Alignment.BottomStart)
        .padding(12.dp),
      text = stringResource(action.title()),
      style = WalletTheme.labelStyle(
        LabelType.Body2Regular
      )
    )
  }
}

private fun SecurityAction.title(): StringResource =
  when (this.type()) {
    BIOMETRIC -> Res.string.biometric_action_title
    CRITICAL_ALERTS -> Res.string.critical_alert_action_title
    EEK_BACKUP -> Res.string.eak_backup_action_title
    FINGERPRINTS -> Res.string.fingerprints_action_title
    INHERITANCE -> Res.string.inheritance_action_title
    APP_KEY_BACKUP -> Res.string.mobile_key_backup_action_title
    SOCIAL_RECOVERY -> Res.string.social_recovery_action_title
    HARDWARE_DEVICE -> Res.string.hardware_device_action_title
    TRANSACTION_VERIFICATION -> Res.string.transaction_verification_title
    KEYSET_SYNC -> Res.string.keyset_sync_action_title
  }

private fun SecurityAction.icon(): Icon =
  when (this.type()) {
    BIOMETRIC -> Icon.DotSecurity
    CRITICAL_ALERTS -> Icon.DotCriticalAlerts
    EEK_BACKUP -> Icon.DotEmergency
    FINGERPRINTS -> Icon.DotFingerprint
    INHERITANCE -> Icon.DotInheritance
    APP_KEY_BACKUP -> Icon.DotCloudBackup
    SOCIAL_RECOVERY -> Icon.DotRecoveryContact
    HARDWARE_DEVICE -> Icon.DotBitkey
    TRANSACTION_VERIFICATION -> Icon.ShieldCheck
    KEYSET_SYNC -> Icon.SmallIconWarning
  }

private fun SecurityAction.statusColor(): Color =
  when (state()) {
    SecurityActionState.Secure -> SuccessGreen
    SecurityActionState.HasRecommendationActions -> WarningOrange
    SecurityActionState.HasCriticalActions -> DestructiveRed
    SecurityActionState.Disabled -> DisabledGrey
  }

private val SECURITY_HUB_TOP_PADDING: Dp = 8.dp
private val SECURITY_HUB_HORIZONTAL_PADDING: Dp = 20.dp
private val SECURITY_HUB_TOOLBAR_HEIGHT: Dp = 48.dp
private val SECURITY_HUB_TOOLBAR_BOTTOM_PADDING: Dp = 8.dp
private val SECURITY_HUB_TOOLBAR_BOTTOM_GRADIENT_HEIGHT: Dp = 20.dp
private val SECURITY_HUB_TOOLBAR_RESERVED_HEIGHT: Dp =
  SECURITY_HUB_TOP_PADDING + SECURITY_HUB_TOOLBAR_HEIGHT + SECURITY_HUB_TOOLBAR_BOTTOM_PADDING
private val SECURITY_HUB_CONTENT_TOP_PADDING: Dp = 24.dp

private fun SecurityActionRecommendation.title(): StringResource =
  when (this) {
    BACKUP_MOBILE_KEY -> Res.string.backup_mobile_key_recommendation_title
    BACKUP_EAK -> Res.string.backup_eak_recommendation_title
    ADD_FINGERPRINTS -> Res.string.add_fingerprints_recommendation_title
    COMPLETE_FINGERPRINT_RESET -> Res.string.complete_fingerprint_reset_recommendation_title
    PROVISION_APP_KEY_TO_HARDWARE -> Res.string.provision_app_key_to_hardware_recommendation_title
    ADD_TRUSTED_CONTACTS -> Res.string.add_recovery_contacts_recommendation_title
    ENABLE_CRITICAL_ALERTS -> Res.string.enable_critical_alerts_recommendation_title
    ADD_BENEFICIARY -> Res.string.add_beneficiary_recommendation_title
    SETUP_BIOMETRICS -> Res.string.setup_biometric_recommendation_title
    ENABLE_PUSH_NOTIFICATIONS -> Res.string.enable_push_recommendation_title
    ENABLE_SMS_NOTIFICATIONS -> Res.string.enable_sms_recommendation_title
    ENABLE_EMAIL_NOTIFICATIONS -> Res.string.enable_email_recommendation_title
    UPDATE_FIRMWARE -> Res.string.update_firmware_recommendation_title
    PAIR_HARDWARE_DEVICE -> Res.string.pair_device_recommendation_title
    ENABLE_TRANSACTION_VERIFICATION -> Res.string.transaction_verification_recommendation_title
    REPAIR_KEYSET_MISMATCH -> Res.string.repair_keyset_mismatch_recommendation_title
  }

@Snapshot
val SnapshotHost.pendingRecommendations
  get() = SecurityHubBodyModel(
    atRiskRecommendations = immutableListOf(),
    recommendations = listOf(
      BACKUP_MOBILE_KEY,
      BACKUP_EAK,
      ADD_FINGERPRINTS,
      ADD_TRUSTED_CONTACTS,
      ENABLE_CRITICAL_ALERTS,
      ADD_BENEFICIARY,
      SETUP_BIOMETRICS
    ).toImmutableList(),
    securityActions = listOf(
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        ENABLE_CRITICAL_ALERTS,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = EEK_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_EAK,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        ENABLE_CRITICAL_ALERTS,
        state = SecurityActionState.HasRecommendationActions
      )
    ),
    recoveryActions = listOf(
      previewSecurityAction(
        type = FINGERPRINTS,
        category = SecurityActionCategory.SECURITY,
        ADD_FINGERPRINTS,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = INHERITANCE,
        category = SecurityActionCategory.SECURITY,
        ADD_BENEFICIARY,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = APP_KEY_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_MOBILE_KEY,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = SOCIAL_RECOVERY,
        category = SecurityActionCategory.SECURITY,
        ADD_TRUSTED_CONTACTS,
        state = SecurityActionState.HasRecommendationActions
      )
    ),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    onHomeTabClick = {},
    cardsModel = CardListModel(cards = immutableListOf())
  )

@Snapshot
val SnapshotHost.pendingRecommendationsWithCards
  get() = SecurityHubBodyModel(
    atRiskRecommendations = immutableListOf(),
    recommendations = listOf(
      BACKUP_MOBILE_KEY,
      BACKUP_EAK,
      ADD_FINGERPRINTS,
      ADD_TRUSTED_CONTACTS,
      ENABLE_CRITICAL_ALERTS,
      ADD_BENEFICIARY,
      SETUP_BIOMETRICS
    ).toImmutableList(),
    securityActions = listOf(
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        ENABLE_CRITICAL_ALERTS,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = EEK_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_EAK,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        ENABLE_CRITICAL_ALERTS,
        state = SecurityActionState.HasRecommendationActions
      )
    ),
    recoveryActions = listOf(
      previewSecurityAction(
        type = FINGERPRINTS,
        category = SecurityActionCategory.SECURITY,
        ADD_FINGERPRINTS,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = INHERITANCE,
        category = SecurityActionCategory.SECURITY,
        ADD_BENEFICIARY,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = APP_KEY_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_MOBILE_KEY,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = SOCIAL_RECOVERY,
        category = SecurityActionCategory.SECURITY,
        ADD_TRUSTED_CONTACTS,
        state = SecurityActionState.HasRecommendationActions
      )
    ),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    onHomeTabClick = {},
    cardsModel = CardListModel(
      cards = immutableListOf(
        HardwareRecoveryCardModel(
          title = "Replacement pending...",
          subtitle = "2 days remaining",
          delayPeriodProgress = Progress.Half,
          delayPeriodRemainingSeconds = 0,
          onClick = {}
        )
      )
    )
  )

@Snapshot
val SnapshotHost.pendingRecommendationsWithFingerprintResetCard
  get() = SecurityHubBodyModel(
    atRiskRecommendations = immutableListOf(),
    recommendations = listOf(
      BACKUP_MOBILE_KEY,
      BACKUP_EAK,
      ADD_FINGERPRINTS,
      ADD_TRUSTED_CONTACTS,
      ENABLE_CRITICAL_ALERTS,
      ADD_BENEFICIARY,
      SETUP_BIOMETRICS
    ).toImmutableList(),
    securityActions = listOf(
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        ENABLE_CRITICAL_ALERTS,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = EEK_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_EAK,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        ENABLE_CRITICAL_ALERTS,
        state = SecurityActionState.HasRecommendationActions
      )
    ),
    recoveryActions = listOf(
      previewSecurityAction(
        type = FINGERPRINTS,
        category = SecurityActionCategory.SECURITY,
        ADD_FINGERPRINTS,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = INHERITANCE,
        category = SecurityActionCategory.SECURITY,
        ADD_BENEFICIARY,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = APP_KEY_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_MOBILE_KEY,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = SOCIAL_RECOVERY,
        category = SecurityActionCategory.SECURITY,
        ADD_TRUSTED_CONTACTS,
        state = SecurityActionState.HasRecommendationActions
      )
    ),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    onHomeTabClick = {},
    cardsModel = CardListModel(
      cards = immutableListOf(
        FingerprintResetCardModel(
          title = "Add a backup fingerprint",
          subtitle = "Keep replacement options available",
          backgroundColor = CardModel.CardStyle.Gradient.BackgroundColor.InverseBackground,
          onClick = {}
        )
      )
    )
  )

@Snapshot
val SnapshotHost.pendingAtRiskRecommendations
  get() = SecurityHubBodyModel(
    atRiskRecommendations = immutableListOf(
      BACKUP_MOBILE_KEY
    ),
    recommendations = listOf(
      BACKUP_MOBILE_KEY,
      BACKUP_EAK,
      ADD_FINGERPRINTS,
      ADD_TRUSTED_CONTACTS,
      ENABLE_CRITICAL_ALERTS,
      ADD_BENEFICIARY,
      SETUP_BIOMETRICS
    ).toImmutableList(),
    securityActions = listOf(
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        ENABLE_CRITICAL_ALERTS,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = EEK_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_EAK,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        ENABLE_CRITICAL_ALERTS,
        state = SecurityActionState.HasRecommendationActions
      )
    ),
    recoveryActions = listOf(
      previewSecurityAction(
        type = FINGERPRINTS,
        category = SecurityActionCategory.SECURITY,
        ADD_FINGERPRINTS,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = INHERITANCE,
        category = SecurityActionCategory.SECURITY,
        ADD_BENEFICIARY,
        state = SecurityActionState.HasRecommendationActions
      ),
      previewSecurityAction(
        type = APP_KEY_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_MOBILE_KEY,
        state = SecurityActionState.HasCriticalActions
      ),
      previewSecurityAction(
        type = SOCIAL_RECOVERY,
        category = SecurityActionCategory.SECURITY,
        ADD_TRUSTED_CONTACTS,
        state = SecurityActionState.HasRecommendationActions
      )
    ),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    onHomeTabClick = {},
    cardsModel = CardListModel(
      cards = immutableListOf(
        HardwareRecoveryCardModel(
          title = "Replacement pending...",
          subtitle = "2 days remaining",
          delayPeriodProgress = Progress.Half,
          delayPeriodRemainingSeconds = 0,
          onClick = {}
        )
      )
    )
  )

@Snapshot
val SnapshotHost.completedRecommendations
  get() = SecurityHubBodyModel(
    atRiskRecommendations = immutableListOf(),
    recommendations = immutableListOf(),
    cardsModel = CardListModel(cards = immutableListOf()),
    showAllSetState = true,
    securityActions = listOf(
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Secure
      ),
      previewSecurityAction(
        type = EEK_BACKUP,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Secure
      ),
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Secure
      )
    ),
    recoveryActions = listOf(
      previewSecurityAction(
        type = FINGERPRINTS,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Secure
      ),
      previewSecurityAction(
        type = INHERITANCE,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Secure
      ),
      previewSecurityAction(
        type = APP_KEY_BACKUP,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Secure
      ),
      previewSecurityAction(
        type = SOCIAL_RECOVERY,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Secure
      )
    ),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    onHomeTabClick = {}
  )

@Snapshot
val SnapshotHost.loadingRecommendations
  get() = SecurityHubBodyModel(
    atRiskRecommendations = immutableListOf(),
    recommendations = immutableListOf(),
    cardsModel = CardListModel(cards = immutableListOf()),
    securityActions = emptyList(),
    recoveryActions = emptyList(),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    onHomeTabClick = {}
  )

@Snapshot
val SnapshotHost.offline
  get() = SecurityHubBodyModel(
    atRiskRecommendations = immutableListOf(),
    isOffline = true,
    recommendations = immutableListOf(),
    cardsModel = CardListModel(cards = immutableListOf()),
    securityActions = listOf(
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Disabled
      ),
      previewSecurityAction(
        type = EEK_BACKUP,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Disabled
      ),
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Disabled
      )
    ),
    recoveryActions = listOf(
      previewSecurityAction(
        type = FINGERPRINTS,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Disabled
      ),
      previewSecurityAction(
        type = INHERITANCE,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Disabled
      ),
      previewSecurityAction(
        type = APP_KEY_BACKUP,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Disabled
      ),
      previewSecurityAction(
        type = SOCIAL_RECOVERY,
        category = SecurityActionCategory.SECURITY,
        state = SecurityActionState.Disabled
      )
    ),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    onHomeTabClick = {}
  )

private fun previewSecurityAction(
  type: SecurityActionType,
  category: SecurityActionCategory,
  vararg recommendations: SecurityActionRecommendation,
  state: SecurityActionState = SecurityActionState.Secure,
) = object : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> = recommendations.toList()

  override fun category(): SecurityActionCategory = category

  override fun type(): SecurityActionType = type

  override fun state(): SecurityActionState = state
}
