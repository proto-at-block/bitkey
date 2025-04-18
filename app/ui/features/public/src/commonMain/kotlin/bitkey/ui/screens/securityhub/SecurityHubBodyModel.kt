package bitkey.ui.screens.securityhub

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bitkey.securitycenter.SecurityAction
import bitkey.securitycenter.SecurityActionCategory
import bitkey.securitycenter.SecurityActionRecommendation
import bitkey.securitycenter.SecurityActionRecommendation.*
import bitkey.securitycenter.SecurityActionType
import bitkey.securitycenter.SecurityActionType.*
import bitkey.ui.Snapshot
import bitkey.ui.SnapshotHost
import bitkey.ui.features_public.generated.resources.*
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.home.full.HomeTab
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.refresh.PullRefreshIndicator
import build.wallet.ui.components.refresh.pullRefresh
import build.wallet.ui.components.tabbar.Tab
import build.wallet.ui.components.tabbar.TabBar
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class SecurityHubBodyModel(
  val isRefreshing: Boolean,
  val onRefresh: () -> Unit,
  val recommendations: ImmutableList<SecurityActionRecommendation>,
  val securityActions: List<SecurityAction> = emptyList(),
  val recoveryActions: List<SecurityAction> = emptyList(),
  val onRecommendationClick: (SecurityActionRecommendation) -> Unit,
  val onSecurityActionClick: (SecurityAction) -> Unit,
  val tabs: List<HomeTab>,
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
            .background(color = WalletTheme.colors.secondary)
            .padding(horizontal = 20.dp)
            .statusBarsPadding()
        ) {
          Label(
            model = LabelModel.StringModel("Security hub"),
            style = WalletTheme.labelStyle(LabelType.Title1, textColor = WalletTheme.colors.foreground)
          )

          RecommendationList(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            recommendations = recommendations,
            onRecommendationClick = onRecommendationClick
          )

          Spacer(modifier = Modifier.height(32.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
          if (securityActions.isNotEmpty()) {
            HubActionSection(
              sectionTitle = "Security",
              actions = securityActions.toImmutableList(),
              onTileClick = onSecurityActionClick
            )
          }

          Spacer(modifier = Modifier.height(20.dp))

          if (recoveryActions.isNotEmpty()) {
            HubActionSection(
              sectionTitle = "Recovery",
              actions = recoveryActions.toImmutableList(),
              onTileClick = onSecurityActionClick
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
          Tab(selected = it.selected, onClick = it.onSelected, icon = it.icon)
        }
      }
    }
  }
}

@Composable
private fun RecommendationList(
  modifier: Modifier = Modifier,
  recommendations: ImmutableList<SecurityActionRecommendation>,
  onRecommendationClick: (SecurityActionRecommendation) -> Unit,
) {
  Column(
    modifier = modifier.border(
      width = 2.dp,
      color = WalletTheme.colors.foreground10,
      shape = RoundedCornerShape(16.dp)
    ).background(
      color = WalletTheme.colors.background,
      shape = RoundedCornerShape(16.dp)
    )
  ) {
    RecommendationHeader(
      modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
      recommendations.size
    )
    if (recommendations.isNotEmpty()) {
      Divider(modifier = Modifier.fillMaxWidth(), thickness = 2.dp)
      recommendations.mapIndexed { index, recommendation ->
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          RecommendationRow(recommendation, onRecommendationClick = onRecommendationClick)
          if (index != recommendations.lastIndex) {
            Divider(modifier = Modifier.fillMaxWidth())
          }
        }
      }
      Spacer(Modifier.height(10.dp))
    }
  }
}

@Composable
fun RecommendationHeader(
  modifier: Modifier = Modifier,
  numberOfRecommendations: Int,
) {
  Row(
    modifier = modifier,
    verticalAlignment = CenterVertically
  ) {
    RecommendationStateIndicator(numberOfRecommendations)
    Spacer(modifier = Modifier.width(10.dp))
    Label(
      text = when (numberOfRecommendations) {
        0 -> "You're all set"
        1 -> "1 recommended action"
        else -> "$numberOfRecommendations recommended actions"
      },
      style = WalletTheme.labelStyle(
        LabelType.Title2,
        textColor = WalletTheme.colors.foreground
      )
    )
  }
}

@Composable
fun RecommendationRow(
  recommendation: SecurityActionRecommendation,
  onRecommendationClick: (SecurityActionRecommendation) -> Unit,
) {
  Row(
    Modifier.padding(vertical = 15.dp).scalingClickable {
      onRecommendationClick(recommendation)
    }
  ) {
    Icon(
      icon = recommendation.icon(),
      size = IconSize.Small,
      color = WalletTheme.colors.foreground
    )
    Spacer(modifier = Modifier.width(12.dp))
    Label(
      stringResource(recommendation.title()),
      color = WalletTheme.colors.foreground,
      type = LabelType.Body2Medium
    )
    Spacer(modifier = Modifier.weight(1.0f))
    Icon(
      icon = Icon.SmallIconCaretRight,
      size = IconSize.Small,
      color = WalletTheme.colors.foreground30
    )
  }
}

@Composable
private fun RecommendationStateIndicator(numberOfRecommendations: Int) {
  val color = WalletTheme.colors.secondary
  Box(modifier = Modifier.padding(5.dp).size(42.dp)) {
    if (numberOfRecommendations == 0) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        drawCircle(
          color = Color(0xff3aba5a),
          style = Stroke(4.dp.toPx()),
          radius = canvasWidth / 2
        )
      }

      Icon(
        modifier = Modifier.align(Center),
        icon = Icon.LargeIconCheckFilled,
        size = IconSize.Medium,
        color = Color(0xff3aba5a)
      )
    } else {
      Canvas(modifier = Modifier.fillMaxSize()) {
        // the spacing of the centers of each circle, in degrees
        val recommendationCircleSpacingInDegrees = 5.dp.toPx().toInt()
        // the start of the circle in degrees (12 o'clock position)
        val circleStartInDegrees = 270
        // the size of the recommendation circle radius in pixels
        val recommendationCircleRadius = 2.dp.toPx()

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
            color = Color(0xffbf46e38),
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
          style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
        )
      }

      Icon(
        modifier = Modifier.align(Center),
        icon = Icon.LargeIconWarningFilled,
        size = IconSize.Medium,
        color = Color(0xffbf46e38)
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
      .scalingClickable {
        onClick(action)
      }
      .background(
        color = WalletTheme.colors.secondary,
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
        icon = action.icon(),
        size = IconSize.Small
      )

      Box(
        modifier = Modifier.background(
          color = action.statusColor(),
          shape = CircleShape
        ).size(10.dp)
      )
    }

    Label(
      modifier = Modifier.align(Alignment.BottomStart)
        .padding(12.dp),
      text = stringResource(action.title()),
      style = WalletTheme.labelStyle(LabelType.Body2Medium)
    )
  }
}

private fun SecurityAction.title(): StringResource =
  when (this.type()) {
    BIOMETRIC -> Res.string.biometric_action_title
    CRITICAL_ALERTS -> Res.string.critical_alert_action_title
    EAK_BACKUP -> Res.string.eak_backup_action_title
    FINGERPRINTS -> Res.string.fingerprints_action_title
    INHERITANCE -> Res.string.inheritance_action_title
    MOBILE_KEY_BACKUP -> Res.string.mobile_key_backup_action_title
    SOCIAL_RECOVERY -> Res.string.social_recovery_action_title
  }

private fun SecurityAction.icon(): Icon =
  when (this.type()) {
    BIOMETRIC -> Icon.SmallIconLock
    CRITICAL_ALERTS -> Icon.SmallIconAnnouncement
    EAK_BACKUP -> Icon.SmallIconRecovery
    FINGERPRINTS -> Icon.SmallIconFingerprint
    INHERITANCE -> Icon.SmallIconInheritance
    MOBILE_KEY_BACKUP -> Icon.SmallIconCloud
    SOCIAL_RECOVERY -> Icon.SmallIconShieldPerson
  }

private fun SecurityAction.statusColor(): Color =
  when {
    getRecommendations().isEmpty() -> Color(0xff3aba5a)
    else -> Color(0xffbf46e38)
  }

private fun SecurityActionRecommendation.title(): StringResource =
  when (this) {
    BACKUP_MOBILE_KEY -> Res.string.backup_mobile_key_recommendation_title
    BACKUP_EAK -> Res.string.backup_eak_recommendation_title
    ADD_FINGERPRINTS -> Res.string.add_fingerprints_recommendation_title
    ADD_TRUSTED_CONTACTS -> Res.string.add_recovery_contacts_recommendation_title
    ENABLE_CRITICAL_ALERTS -> Res.string.enable_critical_alerts_recommendation_title
    ADD_BENEFICIARY -> Res.string.add_beneficiary_recommendation_title
    SETUP_BIOMETRICS -> Res.string.setup_biometric_recommendation_title
  }

private fun SecurityActionRecommendation.icon(): Icon =
  when (this) {
    BACKUP_MOBILE_KEY -> Icon.SmallIconCloud
    BACKUP_EAK -> Icon.SmallIconRecovery
    ADD_FINGERPRINTS -> Icon.SmallIconFingerprint
    ADD_TRUSTED_CONTACTS -> Icon.SmallIconShieldPerson
    ENABLE_CRITICAL_ALERTS -> Icon.SmallIconAnnouncement
    ADD_BENEFICIARY -> Icon.SmallIconInheritance
    SETUP_BIOMETRICS -> Icon.SmallIconLock
  }

@Snapshot
val SnapshotHost.pendingRecommendations
  get() = SecurityHubBodyModel(
    isRefreshing = false,
    onRefresh = {},
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
        ENABLE_CRITICAL_ALERTS
      ),
      previewSecurityAction(
        type = EAK_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_EAK
      ),
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY,
        ENABLE_CRITICAL_ALERTS
      )
    ),
    recoveryActions = listOf(
      previewSecurityAction(
        type = FINGERPRINTS,
        category = SecurityActionCategory.SECURITY,
        ADD_FINGERPRINTS
      ),
      previewSecurityAction(
        type = INHERITANCE,
        category = SecurityActionCategory.SECURITY,
        ADD_BENEFICIARY
      ),
      previewSecurityAction(
        type = MOBILE_KEY_BACKUP,
        category = SecurityActionCategory.SECURITY,
        BACKUP_MOBILE_KEY
      ),
      previewSecurityAction(
        type = SOCIAL_RECOVERY,
        category = SecurityActionCategory.SECURITY,
        ADD_TRUSTED_CONTACTS
      )
    ),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    tabs = listOf(
      HomeTab.MoneyHome(
        selected = false,
        onSelected = {}
      ),
      HomeTab.SecurityHub(
        selected = true,
        onSelected = {},
        badged = false
      )
    )
  )

@Snapshot
val SnapshotHost.completedRecommendations
  get() = SecurityHubBodyModel(
    isRefreshing = false,
    onRefresh = {},
    recommendations = immutableListOf(),
    securityActions = listOf(
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY
      ),
      previewSecurityAction(
        type = EAK_BACKUP,
        category = SecurityActionCategory.SECURITY
      ),
      previewSecurityAction(
        type = CRITICAL_ALERTS,
        category = SecurityActionCategory.SECURITY
      )
    ),
    recoveryActions = listOf(
      previewSecurityAction(
        type = FINGERPRINTS,
        category = SecurityActionCategory.SECURITY
      ),
      previewSecurityAction(
        type = INHERITANCE,
        category = SecurityActionCategory.SECURITY
      ),
      previewSecurityAction(
        type = MOBILE_KEY_BACKUP,
        category = SecurityActionCategory.SECURITY
      ),
      previewSecurityAction(
        type = SOCIAL_RECOVERY,
        category = SecurityActionCategory.SECURITY
      )
    ),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    tabs = listOf(
      HomeTab.MoneyHome(
        selected = false,
        onSelected = {}
      ),
      HomeTab.SecurityHub(
        selected = true,
        onSelected = {},
        badged = false
      )
    )
  )

@Snapshot
val SnapshotHost.loadingRecommendations
  get() = SecurityHubBodyModel(
    isRefreshing = true,
    onRefresh = {},
    recommendations = immutableListOf(),
    securityActions = emptyList(),
    recoveryActions = emptyList(),
    onRecommendationClick = {},
    onSecurityActionClick = {},
    tabs = listOf(
      HomeTab.MoneyHome(
        selected = false,
        onSelected = {}
      ),
      HomeTab.SecurityHub(
        selected = true,
        onSelected = {},
        badged = false
      )
    )
  )

private fun previewSecurityAction(
  type: SecurityActionType,
  category: SecurityActionCategory,
  vararg recommendations: SecurityActionRecommendation,
) = object : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> = recommendations.toList()

  override fun category(): SecurityActionCategory = category

  override fun type(): SecurityActionType = type
}
