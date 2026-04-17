package bitkey.ui.screens.securityhub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.home.full.HomeTab
import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryCardModel
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
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.market.MarketIcons
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val DestructiveRed = Color(0xffca0000)
private val WarningOrange = Color(0xffbf46e38)
private val SuccessGreen = Color(0xff3aba5a)
private val DisabledGrey = Color(0xffc6c6c6)

internal fun securityHubToolbarAccessoryModel(onClick: () -> Unit = {}) =
  ToolbarAccessoryModel.IconAccessory(
    model = IconButtonModel(
      iconModel = IconModel(
        icon = MarketIcons.EllipsisHorizontal,
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
  val securityActions: List<SecurityAction> = emptyList(),
  val recoveryActions: List<SecurityAction> = emptyList(),
  val onRecommendationClick: (SecurityActionRecommendation) -> Unit,
  val onSecurityActionClick: (SecurityAction) -> Unit,
  val onHomeTabClick: () -> Unit,
  val trailingToolbarAccessoryModel: ToolbarAccessoryModel = securityHubToolbarAccessoryModel(),
  val haptics: Haptics? = null,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = EventTrackerScreenInfo(
    eventTrackerScreenId = SecurityHubEventTrackerScreenId.SECURITY_HUB_SCREEN
  ),
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    val localDensity = LocalDensity.current
    val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
    val scrollState = rememberScrollState()
    var tabBarHeightDp by remember {
      mutableStateOf(0.dp)
    }
    Box(
      modifier = modifier.fillMaxSize()
        .background(
          color = screenBackgroundColor(isOffline)
        )
    ) {
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
          .verticalScroll(scrollState)
          .thenIf(isDesignSystemV2Enabled) {
            Modifier.padding(top = SECURITY_HUB_TOOLBAR_RESERVED_HEIGHT + SECURITY_HUB_CONTENT_TOP_PADDING)
          }
      ) {
        SecurityHubHeaderSection(
          isOffline = isOffline,
          atRiskRecommendations = atRiskRecommendations,
          recommendations = recommendations,
          cardsModel = cardsModel,
          onRecommendationClick = onRecommendationClick,
          showTitle = !isDesignSystemV2Enabled
        )

        SecurityHubActionsSection(
          securityActions = securityActions.toImmutableList(),
          recoveryActions = recoveryActions.toImmutableList(),
          onSecurityActionClick = onSecurityActionClick,
          tabBarHeightDp = tabBarHeightDp
        )
      }

      if (isDesignSystemV2Enabled) {
        SecurityHubTopToolbar(
          modifier = Modifier.align(Alignment.TopCenter),
          title = "Security Hub",
          trailingToolbarAccessoryModel = trailingToolbarAccessoryModel
        )
      }

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
        if (isDesignSystemV2Enabled) {
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
        } else {
          tabs.forEach { tab ->
            Tab(
              selected = tab.selected,
              onClick = tab.onSelected,
              icon = tab.icon
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SecurityHubHeaderSection(
  isOffline: Boolean,
  atRiskRecommendations: ImmutableList<SecurityActionRecommendation>,
  recommendations: ImmutableList<SecurityActionRecommendation>,
  cardsModel: CardListModel,
  onRecommendationClick: (SecurityActionRecommendation) -> Unit,
  showTitle: Boolean,
) {
  val isDesignSystemUpdatesEnabled = LocalDesignSystemUpdatesEnabled.current

  Column(
    modifier = Modifier.fillMaxWidth()
      .background(color = screenBackgroundColor(isOffline))
      .padding(horizontal = 20.dp)
  ) {
    if (showTitle) {
      Spacer(modifier = Modifier.height(8.dp))
      Label(
        model = LabelModel.StringModel("Security Hub"),
        style = WalletTheme.labelStyle(LabelType.Title1, textColor = WalletTheme.colors.foreground)
      )
    }

    if (!isOffline) {
      Spacer(modifier = Modifier.height(if (showTitle) 20.dp else 0.dp))

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
        cardsModel.cards.map {
          NewCard(model = it)
          Spacer(modifier = Modifier.height(8.dp))
        }

        if (cardsModel.cards.isNotEmpty()) {
          Spacer(modifier = Modifier.height(12.dp))
        }

        if (recommendations.isNotEmpty() || cardsModel.cards.isEmpty()) {
          RecommendationList(
            modifier = Modifier.fillMaxWidth(),
            recommendations = recommendations,
            onRecommendationClick = onRecommendationClick,
            type = RecommendationType.Recommended
          )
        }
      }

      Spacer(
        modifier = Modifier.height(
          if (isDesignSystemUpdatesEnabled) 16.dp else 32.dp
        )
      )
    }
  }
}

@Composable
private fun SecurityHubTopToolbar(
  modifier: Modifier = Modifier,
  title: String,
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
          text = title,
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
  tabBarHeightDp: Dp,
) {
  if (securityActions.isEmpty() && recoveryActions.isEmpty()) return

  Column(
    modifier = Modifier.background(WalletTheme.colors.background)
      .padding(horizontal = 20.dp)
  ) {
    Spacer(modifier = Modifier.height(32.dp))

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
  } else if (LocalDesignSystemUpdatesEnabled.current) {
    WalletTheme.colors.background
  } else {
    WalletTheme.colors.secondary
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
  val isDesignSystemUpdatesEnabled = LocalDesignSystemUpdatesEnabled.current
  val useContainer = !isDesignSystemUpdatesEnabled
  val recommendationBackgroundColor = if (isDesignSystemUpdatesEnabled) {
    WalletTheme.colors.secondary
  } else {
    WalletTheme.colors.background
  }
  val recommendationListModifier = if (useContainer) {
    modifier.shadow(
      elevation = 2.dp,
      shape = RoundedCornerShape(16.dp),
      ambientColor = Color.Black.copy(.1f)
    ).background(
      color = recommendationBackgroundColor,
      shape = RoundedCornerShape(16.dp)
    )
  } else {
    modifier
  }
  Column(
    modifier = recommendationListModifier
  ) {
    RecommendationHeader(
      modifier = if (useContainer) {
        Modifier.padding(vertical = 20.dp, horizontal = 12.dp)
      } else {
        Modifier.padding(top = 8.dp, bottom = 8.dp)
      },
      numberOfRecommendations = recommendations.size,
      type = type
    )
    if (recommendations.isNotEmpty()) {
      if (useContainer) {
        Divider(modifier = Modifier.fillMaxWidth(), thickness = 2.dp)
      }
      recommendations.mapIndexed { index, recommendation ->
        Column(
          modifier = if (useContainer) {
            Modifier.padding(horizontal = 16.dp)
          } else {
            Modifier
          }
        ) {
          RecommendationRow(
            recommendation = recommendation,
            recommendationIndex = index + 1,
            onRecommendationClick = onRecommendationClick
          )
          if (useContainer) {
            if (index != recommendations.lastIndex) {
              Divider(modifier = Modifier.fillMaxWidth())
            }
          } else {
            Divider(
              modifier = Modifier.fillMaxWidth(),
              color = WalletTheme.colors.subtleBackground
            )
          }
        }
      }
      if (useContainer) {
        Spacer(Modifier.height(10.dp))
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
  val isDesignSystemUpdatesEnabled = LocalDesignSystemUpdatesEnabled.current
  val text = when (type) {
    RecommendationType.Critical -> "Wallet at risk"
    RecommendationType.Recommended -> when (numberOfRecommendations) {
      0 -> "You're all set"
      1 -> "1 recommended action"
      else -> "$numberOfRecommendations recommended actions"
    }
  }

  Row(
    modifier = modifier,
    verticalAlignment = CenterVertically
  ) {
    RecommendationStateIndicator(numberOfRecommendations, type)
    Spacer(modifier = Modifier.width(12.dp))
    Label(
      text = text,
      type = if (isDesignSystemUpdatesEnabled) LabelType.Body3Mono else LabelType.Title2,
      treatment = if (isDesignSystemUpdatesEnabled) LabelTreatment.Secondary else LabelTreatment.Primary
    )
  }
}

@Composable
fun RecommendationRow(
  recommendation: SecurityActionRecommendation,
  recommendationIndex: Int,
  onRecommendationClick: (SecurityActionRecommendation) -> Unit,
) {
  val isDesignSystemUpdatesEnabled = LocalDesignSystemUpdatesEnabled.current

  Row(
    modifier = Modifier.padding(
      vertical = if (isDesignSystemUpdatesEnabled) 16.dp else 20.dp
    )
      .scalingClickable {
        onRecommendationClick(recommendation)
      },
    verticalAlignment = CenterVertically
  ) {
    if (!isDesignSystemUpdatesEnabled) {
      Spacer(modifier = Modifier.width(6.dp))
    }
    if (isDesignSystemUpdatesEnabled) {
      Label(
        text = "[$recommendationIndex]",
        type = LabelType.Body3Mono,
        color = WalletTheme.colors.foreground
      )
    } else {
      Icon(
        icon = recommendation.icon(),
        size = IconSize.Small,
        color = WalletTheme.colors.foreground
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Label(
      modifier = Modifier.weight(1.0f),
      text = stringResource(recommendation.title()),
      color = WalletTheme.colors.foreground,
      type = if (isDesignSystemUpdatesEnabled) LabelType.Body3Mono else LabelType.Body2Medium,
      overflow = TextOverflow.Ellipsis
    )
    Icon(
      icon = Icon.SmallIconCaretRight,
      size = if (isDesignSystemUpdatesEnabled) IconSize.Accessory else IconSize.Small,
      color = WalletTheme.colors.foreground30
    )
  }
}

@Composable
private fun RecommendationStateIndicator(
  numberOfRecommendations: Int,
  type: RecommendationType,
) {
  val isDesignSystemUpdatesEnabled = LocalDesignSystemUpdatesEnabled.current
  val color = WalletTheme.colors.secondary

  val warningColor = when (type) {
    RecommendationType.Critical -> DestructiveRed
    RecommendationType.Recommended -> WarningOrange
  }

  if (isDesignSystemUpdatesEnabled && numberOfRecommendations > 0) {
    Box(
      modifier = Modifier
        .size(10.dp)
        .background(
          color = warningColor,
          shape = CircleShape
        )
    )
    return
  }

  Box(modifier = Modifier.size(44.dp)) {
    if (numberOfRecommendations == 0) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        drawCircle(
          color = SuccessGreen,
          style = Stroke(4.dp.toPx()),
          radius = canvasWidth / 2
        )
      }

      Icon(
        modifier = Modifier.align(Center),
        icon = Icon.LargeIconCheckFilled,
        size = IconSize.Regular,
        color = SuccessGreen
      )
    } else {
      Canvas(modifier = Modifier.fillMaxSize()) {
        // the spacing of the centers of each circle, in degrees
        val recommendationCircleSpacingInDegrees = 6.dp.toPx().toInt()
        // the start of the circle in degrees (12 o'clock position)
        val circleStartInDegrees = 270
        // the size of the recommendation circle radius in pixels
        val recommendationCircleRadius = 2.5.dp.toPx()

        // Draw a circle for each recommendation, translating its value in degrees into x,y coordinates
        // We start at 270 degrees (the top of the circle) and go clockwise for each recommendation
        for (
        angleDegrees in
        circleStartInDegrees..circleStartInDegrees + (numberOfRecommendations * recommendationCircleSpacingInDegrees)
          step recommendationCircleSpacingInDegrees
        ) {
          // convert degree value to radians and convert that value to x,y coordinates
          val angleRadians = angleDegrees / 180.0 * PI
          val x = center.x + (size.width / 2) * cos(angleRadians).toFloat()
          val y = center.y + (size.width / 2) * sin(angleRadians).toFloat()
          drawCircle(
            color = warningColor,
            radius = recommendationCircleRadius,
            center = Offset(x, y)
          )
        }

        // Draw the arc that represents the remainder of the circle after recommendation circles are drawn
        // We start at 270 degrees and go counterclockwise to the end of the circle
        drawArc(
          color = color,
          startAngle = circleStartInDegrees.toFloat(),
          sweepAngle = -(360f - ((numberOfRecommendations + 1) * recommendationCircleSpacingInDegrees)),
          useCenter = false,
          style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
        )
      }

      Icon(
        modifier = Modifier.align(Center),
        icon = Icon.LargeIconWarningFilled,
        size = IconSize.Regular,
        color = warningColor
      )
    }
  }
}

@Composable
private fun HubActionSection(
  sectionTitle: String,
  actions: ImmutableList<SecurityAction>,
  onTileClick: (SecurityAction) -> Unit,
) {
  val isDesignSystemUpdatesEnabled = LocalDesignSystemUpdatesEnabled.current

  Column {
    Label(
      model = LabelModel.StringModel(sectionTitle),
      type = if (isDesignSystemUpdatesEnabled) LabelType.Body3Mono else LabelType.Title2,
      treatment = if (isDesignSystemUpdatesEnabled) LabelTreatment.Secondary else LabelTreatment.Primary
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
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val cornerRadius = if (isDesignSystemV2Enabled) 8.dp else 16.dp
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
        shape = RoundedCornerShape(cornerRadius)
      )
      .thenIf(action.state() == SecurityActionState.Disabled) {
        Modifier.alpha(0.3f)
      }
  ) {
    Row(
      modifier = Modifier.fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = if (isDesignSystemV2Enabled) Alignment.Top else CenterVertically,
      horizontalArrangement = SpaceBetween
    ) {
      val actionIcon = action.icon(isDesignSystemV2Enabled)
      Icon(
        icon = actionIcon,
        size = actionIcon.iconSize(isDesignSystemV2Enabled),
        tint = actionIcon.iconTint()
      )

      Box(
        modifier = Modifier
          .thenIf(isDesignSystemV2Enabled) {
            Modifier.offset(y = 4.dp)
          }
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
        if (isDesignSystemV2Enabled) LabelType.Body2Regular else LabelType.Body2Medium
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

private fun SecurityAction.icon(isDesignSystemV2Enabled: Boolean): Icon =
  if (isDesignSystemV2Enabled) {
    when (this.type()) {
      BIOMETRIC -> Icon.DotSecurity
      CRITICAL_ALERTS -> Icon.DotCriticalAlerts2
      EEK_BACKUP -> Icon.DotEmergency
      FINGERPRINTS -> Icon.DotFingerprint
      INHERITANCE -> Icon.SmallIconInheritance
      APP_KEY_BACKUP -> Icon.DotCloudBackup
      SOCIAL_RECOVERY -> Icon.DotRecoveryContact2
      HARDWARE_DEVICE -> Icon.DotBitkey
      TRANSACTION_VERIFICATION -> Icon.SmallIconShieldCheck
      KEYSET_SYNC -> Icon.SmallIconWarning
    }
  } else {
    when (this.type()) {
      BIOMETRIC -> Icon.SmallIconLock
      CRITICAL_ALERTS -> Icon.SmallIconAnnouncement
      EEK_BACKUP -> Icon.SmallIconRecovery
      FINGERPRINTS -> Icon.SmallIconFingerprint
      INHERITANCE -> Icon.SmallIconInheritance
      APP_KEY_BACKUP -> Icon.SmallIconCloud
      SOCIAL_RECOVERY -> Icon.SmallIconShieldPerson
      HARDWARE_DEVICE -> Icon.SmallIconBitkey
      TRANSACTION_VERIFICATION -> Icon.SmallIconShieldCheck
      KEYSET_SYNC -> Icon.SmallIconWarning
    }
  }

private fun Icon.iconSize(isDesignSystemV2Enabled: Boolean): IconSize =
  if (isDesignSystemV2Enabled || isDotIcon()) {
    IconSize.Regular
  } else {
    IconSize.Small
  }

private fun Icon.iconTint(): IconTint? =
  if (isDotIcon()) {
    null
  } else {
    IconTint.On60
  }

private fun Icon.isDotIcon(): Boolean = name.startsWith("Dot")

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

private fun SecurityActionRecommendation.icon(): Icon =
  when (this) {
    BACKUP_MOBILE_KEY -> Icon.SmallIconCloud
    BACKUP_EAK -> Icon.SmallIconRecovery
    ADD_FINGERPRINTS -> Icon.SmallIconFingerprint
    COMPLETE_FINGERPRINT_RESET -> Icon.SmallIconFingerprint
    PROVISION_APP_KEY_TO_HARDWARE -> Icon.SmallIconFingerprint
    ADD_TRUSTED_CONTACTS -> Icon.SmallIconShieldPerson
    ENABLE_CRITICAL_ALERTS, ENABLE_SMS_NOTIFICATIONS, ENABLE_EMAIL_NOTIFICATIONS,
    ENABLE_PUSH_NOTIFICATIONS,
    -> Icon.SmallIconAnnouncement
    ADD_BENEFICIARY -> Icon.SmallIconInheritance
    SETUP_BIOMETRICS -> Icon.SmallIconLock
    UPDATE_FIRMWARE, PAIR_HARDWARE_DEVICE -> Icon.SmallIconBitkey
    ENABLE_TRANSACTION_VERIFICATION -> Icon.SmallIconShieldCheck
    REPAIR_KEYSET_MISMATCH -> Icon.SmallIconWarning
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
