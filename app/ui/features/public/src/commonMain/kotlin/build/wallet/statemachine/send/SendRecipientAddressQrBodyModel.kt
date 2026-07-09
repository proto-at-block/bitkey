package build.wallet.statemachine.send

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.BodyModelScreenStyle
import build.wallet.ui.app.qrcode.QrCodeScanScreen
import build.wallet.ui.model.render
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import kotlin.math.abs
import kotlin.math.roundToInt

data class SendRecipientAddressQrBodyModel(
  val scannerBodyModel: QrCodeScanBodyModel,
  val recipientAddressBodyModel: BodyModel,
  val addressSheetExpanded: Boolean,
  val onAddressSheetExpansionStarted: () -> Unit,
  val onAddressSheetRestored: () -> Unit,
  override val onBack: () -> Unit,
) : BodyModel() {
  override val screenStyle: BodyModelScreenStyle =
    BodyModelScreenStyle(
      requiresSystemBarsPadding = true,
      usesBlackFullscreenBackground = true,
      drawsBehindReservedStatusBar = true,
      usesThemeBackgroundForScreenContainer = true
    )

  override val eventTrackerScreenInfo: EventTrackerScreenInfo =
    EventTrackerScreenInfo(
      eventTrackerScreenId = SendEventTrackerScreenId.SEND_ADDRESS_ENTRY,
      eventTrackerShouldTrack = false
    )

  @Composable
  override fun render(modifier: Modifier) {
    SendRecipientAddressQrScreen(
      modifier = modifier,
      model = this
    )
  }
}

@Composable
private fun SendRecipientAddressQrScreen(
  modifier: Modifier = Modifier,
  model: SendRecipientAddressQrBodyModel,
) {
  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    val metrics = rememberRecipientSheetMetrics(maxHeight = maxHeight)
    val sheetState = rememberRecipientSheetState(
      addressSheetExpanded = model.addressSheetExpanded,
      metrics = metrics,
      onAddressSheetExpansionStarted = model.onAddressSheetExpansionStarted,
      onAddressSheetRestored = model.onAddressSheetRestored
    )
    val collapsedProgress = sheetCollapsedProgress(
      sheetOffsetPx = sheetState.offsetPx,
      expandedOffsetPx = metrics.expandedOffsetPx,
      collapsedOffsetPx = metrics.collapsedOffsetPx
    )
    val draggableState = rememberDraggableState { delta ->
      sheetState.onDrag(delta)
    }
    val sheetDragModifier = Modifier.draggable(
      orientation = Orientation.Vertical,
      state = draggableState,
      onDragStopped = { velocity ->
        sheetState.settle(
          shouldExpandSheet(
            velocity = velocity,
            currentOffset = sheetState.offsetPx,
            collapsedOffsetPx = metrics.collapsedOffsetPx,
            expandDragThresholdPx = metrics.expandDragThresholdPx,
            collapseDragThresholdPx = metrics.collapseDragThresholdPx,
            targetExpanded = sheetState.targetExpanded
          )
        )
      }
    )

    ScannerLayer(
      model = model.scannerBodyModel,
      statusBarHeight = metrics.statusBarHeight,
      statusBarHeightPx = metrics.statusBarHeightPx,
      scannerToolbarAlpha = collapsedProgress
    )
    StatusBarOverlay(
      statusBarHeight = metrics.statusBarHeight,
      statusBarHeightPx = metrics.statusBarHeightPx,
      collapsedProgress = collapsedProgress
    )
    RecipientAddressSheet(
      recipientAddressBodyModel = model.recipientAddressBodyModel,
      sheetOffsetPx = sheetState.offsetPx,
      collapsedProgress = collapsedProgress,
      sheetDragModifier = sheetDragModifier
    )

    BackHandler(
      onBack = {
        sheetState.settle(!sheetState.targetExpanded)
      }
    )
  }
}

@Composable
private fun rememberRecipientSheetMetrics(maxHeight: Dp): RecipientSheetMetrics {
  val density = LocalDensity.current
  val statusBars = WindowInsets.statusBars
  return remember(density, maxHeight, statusBars) {
    val expandedOffsetPx = 0f
    val statusBarHeightPx = statusBars.getTop(density)
    val collapsedOffsetPx = with(density) {
      (maxHeight - COLLAPSED_RECIPIENT_SHEET_PEEK_HEIGHT).toPx()
    }
    RecipientSheetMetrics(
      expandedOffsetPx = expandedOffsetPx,
      collapsedOffsetPx = collapsedOffsetPx,
      statusBarHeightPx = statusBarHeightPx,
      statusBarHeight = with(density) { statusBarHeightPx.toDp() },
      expandDragThresholdPx = with(density) { SHEET_EXPAND_DRAG_THRESHOLD.toPx() },
      collapseDragThresholdPx = collapsedOffsetPx / 2f
    )
  }
}

@Composable
private fun rememberRecipientSheetState(
  addressSheetExpanded: Boolean,
  metrics: RecipientSheetMetrics,
  onAddressSheetExpansionStarted: () -> Unit,
  onAddressSheetRestored: () -> Unit,
): RecipientSheetState {
  val sheetOffset = remember { Animatable(metrics.expandedOffsetPx) }
  var dragOffsetPx by remember { mutableStateOf<Float?>(null) }
  var hasInitializedSheetOffset by remember { mutableStateOf(false) }
  var targetExpanded by remember { mutableStateOf(true) }
  var hasAnimationRequest by remember { mutableStateOf(false) }
  var animationRequestCount by remember { mutableIntStateOf(0) }

  fun requestSheetAnimation(expanded: Boolean) {
    targetExpanded = expanded
    hasAnimationRequest = true
    animationRequestCount += 1
  }

  fun settleSheet(expanded: Boolean) {
    notifyAddressSheetExpansionStarted(
      expanded = expanded,
      addressSheetExpanded = addressSheetExpanded,
      onAddressSheetExpansionStarted = onAddressSheetExpansionStarted
    )
    requestSheetAnimation(expanded)
  }

  LaunchedEffect(metrics.expandedOffsetPx, metrics.collapsedOffsetPx) {
    if (!hasInitializedSheetOffset) {
      sheetOffset.snapTo(metrics.expandedOffsetPx)
      hasInitializedSheetOffset = true
    } else {
      val currentOffset = (dragOffsetPx ?: sheetOffset.value)
        .coerceIn(metrics.expandedOffsetPx, metrics.collapsedOffsetPx)
      dragOffsetPx = null
      sheetOffset.snapTo(currentOffset)
    }
  }

  LaunchedEffect(addressSheetExpanded) {
    if (targetExpanded != addressSheetExpanded) {
      requestSheetAnimation(addressSheetExpanded)
    }
  }

  LaunchedEffect(
    animationRequestCount,
    metrics.expandedOffsetPx,
    metrics.collapsedOffsetPx,
    hasInitializedSheetOffset
  ) {
    if (!hasInitializedSheetOffset || !hasAnimationRequest) {
      return@LaunchedEffect
    }

    sheetOffset.stop()
    dragOffsetPx?.let { currentDragOffset ->
      sheetOffset.snapTo(currentDragOffset.coerceIn(metrics.expandedOffsetPx, metrics.collapsedOffsetPx))
      dragOffsetPx = null
    }
    sheetOffset.animateTo(
      targetValue = if (targetExpanded) metrics.expandedOffsetPx else metrics.collapsedOffsetPx
    )

    if (targetExpanded && abs(sheetOffset.value - metrics.expandedOffsetPx) < 0.5f) {
      onAddressSheetRestored()
    }
  }

  return RecipientSheetState(
    offsetPx = dragOffsetPx ?: sheetOffset.value,
    targetExpanded = targetExpanded,
    onDrag = { delta ->
      val currentOffset = dragOffsetPx ?: sheetOffset.value
      dragOffsetPx = (currentOffset + delta).coerceIn(metrics.expandedOffsetPx, metrics.collapsedOffsetPx)
    },
    settle = ::settleSheet
  )
}

@Composable
private fun BoxWithConstraintsScope.ScannerLayer(
  model: QrCodeScanBodyModel,
  statusBarHeight: Dp,
  statusBarHeightPx: Int,
  scannerToolbarAlpha: Float,
) {
  QrCodeScanScreen(
    modifier = Modifier
      .fillMaxWidth()
      .height(maxHeight + statusBarHeight)
      .offset {
        IntOffset(
          x = 0,
          y = -statusBarHeightPx
        )
      },
    model = model,
    scannerToolbarAlpha = scannerToolbarAlpha
  )
}

@Composable
private fun StatusBarOverlay(
  statusBarHeight: Dp,
  statusBarHeightPx: Int,
  collapsedProgress: Float,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(statusBarHeight)
      .offset {
        IntOffset(
          x = 0,
          y = -statusBarHeightPx
        )
      }
      .background(WalletTheme.colors.background.copy(alpha = 1f - collapsedProgress))
  )
}

@Composable
private fun BoxWithConstraintsScope.RecipientAddressSheet(
  recipientAddressBodyModel: BodyModel,
  sheetOffsetPx: Float,
  collapsedProgress: Float,
  sheetDragModifier: Modifier = Modifier,
) {
  val sheetCornerRadius = RECIPIENT_SHEET_CORNER_RADIUS * collapsedProgress
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(maxHeight)
      .align(Alignment.TopCenter)
      .offset {
        IntOffset(
          x = 0,
          y = sheetOffsetPx.roundToInt()
        )
      }
      .clip(RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius))
      .background(WalletTheme.colors.background)
      .then(sheetDragModifier)
  ) {
    recipientAddressBodyModel.render(
      Modifier
        .fillMaxSize()
        .alpha(1f - collapsedProgress)
    )
    CollapsedSheetDragHandle(
      collapsedProgress = collapsedProgress,
      sheetDragModifier = sheetDragModifier
    )
  }
}

@Composable
private fun BoxScope.CollapsedSheetDragHandle(
  collapsedProgress: Float,
  sheetDragModifier: Modifier = Modifier,
) {
  if (collapsedProgress > 0f) {
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .height(COLLAPSED_RECIPIENT_SHEET_PEEK_HEIGHT)
        .then(sheetDragModifier)
    )
    RecipientSheetDragHandle(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .padding(top = 12.dp)
        .alpha(collapsedProgress)
    )
  }
}

private fun notifyAddressSheetExpansionStarted(
  expanded: Boolean,
  addressSheetExpanded: Boolean,
  onAddressSheetExpansionStarted: () -> Unit,
) {
  if (expanded && !addressSheetExpanded) {
    onAddressSheetExpansionStarted()
  }
}

private data class RecipientSheetMetrics(
  val expandedOffsetPx: Float,
  val collapsedOffsetPx: Float,
  val statusBarHeightPx: Int,
  val statusBarHeight: Dp,
  val expandDragThresholdPx: Float,
  val collapseDragThresholdPx: Float,
)

private data class RecipientSheetState(
  val offsetPx: Float,
  val targetExpanded: Boolean,
  val onDrag: (Float) -> Unit,
  val settle: (Boolean) -> Unit,
)

private fun sheetCollapsedProgress(
  sheetOffsetPx: Float,
  expandedOffsetPx: Float,
  collapsedOffsetPx: Float,
): Float =
  if (collapsedOffsetPx == expandedOffsetPx) {
    0f
  } else {
    ((sheetOffsetPx - expandedOffsetPx) / (collapsedOffsetPx - expandedOffsetPx))
      .coerceIn(0f, 1f)
  }

private fun shouldExpandSheet(
  velocity: Float,
  currentOffset: Float,
  collapsedOffsetPx: Float,
  expandDragThresholdPx: Float,
  collapseDragThresholdPx: Float,
  targetExpanded: Boolean,
): Boolean =
  when {
    velocity < -SHEET_SETTLE_VELOCITY_THRESHOLD -> true
    velocity > SHEET_SETTLE_VELOCITY_THRESHOLD -> false
    !targetExpanded -> currentOffset < collapsedOffsetPx - expandDragThresholdPx
    targetExpanded -> currentOffset < collapseDragThresholdPx
    else -> targetExpanded
  }

@Composable
private fun RecipientSheetDragHandle(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .background(
        color = WalletTheme.colors.foreground30,
        shape = RoundedCornerShape(RECIPIENT_SHEET_CORNER_RADIUS)
      )
      .height(4.dp)
      .width(32.dp)
  )
}

private val COLLAPSED_RECIPIENT_SHEET_PEEK_HEIGHT = 112.dp
private val SHEET_EXPAND_DRAG_THRESHOLD = 48.dp
private val RECIPIENT_SHEET_CORNER_RADIUS = 32.dp
private const val SHEET_SETTLE_VELOCITY_THRESHOLD = 500f
