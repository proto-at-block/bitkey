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
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.home.full.HomeTab
import build.wallet.statemachine.moneyhome.card.CardListModel
import build.wallet.statemachine.recovery.hardware.HardwareRecoveryCardModel
import build.wallet.ui.app.moneyhome.card.NewCard
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.tabbar.Tab
import build.wallet.ui.components.tabbar.TabBar
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
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
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = EventTrackerScreenInfo(
    eventTrackerScreenId = SecurityHubEventTrackerScreenId.SECURITY_HUB_SCREEN
  ),
) : BodyModel() {
  @Composable
  override fun render(modifier: Modifier) {
    val localDensity = LocalDensity.current
    var tabBarHeightDp by remember {
      mutableStateOf(0.dp)
    }
    Box(
      modifier = modifier.fillMaxSize()
        .background(
          color = if (isOffline) {
            WalletTheme.colors.background
          } else {
            WalletTheme.colors.secondary
          }
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
          .verticalScroll(rememberScrollState())
      ) {
        Column(
          modifier = Modifier.fillMaxWidth()
            .background(
              color = if (isOffline) {
                WalletTheme.colors.background
              } else {
                WalletTheme.colors.secondary
              }
            )
            .padding(horizontal = 20.dp)
        ) {
          Spacer(modifier = Modifier.height(8.dp))
          Label(
            model = LabelModel.StringModel("Security Hub"),
            style = WalletTheme.labelStyle(LabelType.Title1, textColor = WalletTheme.colors.foreground)
          )

          if (!isOffline) {
            Spacer(modifier = Modifier.height(20.dp))

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

            Spacer(modifier = Modifier.height(32.dp))
          }
        }

        if (securityActions.isNotEmpty() || recoveryActions.isNotEmpty()) {
          Column(
            modifier = Modifier.background(WalletTheme.colors.background)
              .padding(horizontal = 20.dp)
          ) {
            Spacer(modifier = Modifier.height(32.dp))

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
      }

      TabBar(
        modifier = Modifier.align(Alignment.BottomCenter)
          .onGloballyPositioned {
            tabBarHeightDp = with(localDensity) { it.size.height.toDp() + 36.dp }
          }
      ) {
        listOf(
          HomeTab.MoneyHome(
            selected = false,
            onSelected = onHomeTabClick
          ),
          HomeTab.SecurityHub(
            selected = true,
            onSelected = {},
            badged = false
          )
        ).map {
          Tab(selected = it.selected, onClick = it.onSelected, icon = it.icon)
        }
      }
    }
  }
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
    modifier = modifier.shadow(
      elevation = 2.dp,
      shape = RoundedCornerShape(16.dp),
      ambientColor = Color.Black.copy(.1f)
    ).background(
      color = WalletTheme.colors.background,
      shape = RoundedCornerShape(16.dp)
    )
  ) {
    RecommendationHeader(
      modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
      numberOfRecommendations = recommendations.size,
      type = type
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

  Row(
    modifier = modifier,
    verticalAlignment = CenterVertically
  ) {
    RecommendationStateIndicator(numberOfRecommendations, type)
    Spacer(modifier = Modifier.width(12.dp))
    Label(
      text = text,
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
    modifier = Modifier.padding(vertical = 20.dp)
      .scalingClickable {
        onRecommendationClick(recommendation)
      },
    verticalAlignment = CenterVertically
  ) {
    Spacer(modifier = Modifier.width(6.dp))
    Icon(
      icon = recommendation.icon(),
      size = IconSize.Small,
      color = WalletTheme.colors.foreground
    )
    Spacer(modifier = Modifier.width(12.dp))
    Label(
      modifier = Modifier.weight(1.0f),
      text = stringResource(recommendation.title()),
      color = WalletTheme.colors.foreground,
      type = LabelType.Body2Medium,
      overflow = TextOverflow.Ellipsis
    )
    Icon(
      icon = Icon.SmallIconCaretRight,
      size = IconSize.Small,
      color = WalletTheme.colors.foreground30
    )
  }
}

@Composable
private fun RecommendationStateIndicator(
  numberOfRecommendations: Int,
  type: RecommendationType,
) {
  val color = WalletTheme.colors.secondary

  val warningColor = when (type) {
    RecommendationType.Critical -> DestructiveRed
    RecommendationType.Recommended -> WarningOrange
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
      .thenIf(action.state() != SecurityActionState.Disabled) {
        Modifier.scalingClickable {
          onClick(action)
        }
      }
      .background(
        color = WalletTheme.colors.secondary,
        shape = RoundedCornerShape(16.dp)
      )
      .thenIf(action.state() == SecurityActionState.Disabled) {
        Modifier.alpha(0.3f)
      }
  ) {
    Row(
      modifier = Modifier.fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = CenterVertically,
      horizontalArrangement = SpaceBetween
    ) {
      Icon(
        icon = action.icon(),
        size = IconSize.Small,
        tint = IconTint.On60
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
    EEK_BACKUP -> Res.string.eak_backup_action_title
    FINGERPRINTS -> Res.string.fingerprints_action_title
    INHERITANCE -> Res.string.inheritance_action_title
    APP_KEY_BACKUP -> Res.string.mobile_key_backup_action_title
    SOCIAL_RECOVERY -> Res.string.social_recovery_action_title
    HARDWARE_DEVICE -> Res.string.hardware_device_action_title
    TRANSACTION_VERIFICATION -> Res.string.transaction_verification_title
  }

private fun SecurityAction.icon(): Icon =
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
  }

private fun SecurityAction.statusColor(): Color =
  when (state()) {
    SecurityActionState.Secure -> SuccessGreen
    SecurityActionState.HasRecommendationActions -> WarningOrange
    SecurityActionState.HasCriticalActions -> DestructiveRed
    SecurityActionState.Disabled -> DisabledGrey
  }

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
