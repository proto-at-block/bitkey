@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package build.wallet.ui.components.switch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.resolveTestTag
import build.wallet.ui.compose.switchTestTag
import kotlinx.cinterop.cValue
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetWidth
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISwitch
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.sel_registerName

private val IosSwitchOuterWidth = 64.dp
private val IosSwitchInteropWidth = 64.dp
private val IosSwitchInteropHeight = 38.dp
private val IosSwitchHeight = 48.dp

@Composable
internal actual fun PlatformSwitch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier,
  enabled: Boolean,
  interactionsEnabled: Boolean,
  testTag: String?,
  checkedThumbColor: Color,
  uncheckedThumbColor: Color,
  checkedTrackColor: Color,
  uncheckedTrackColor: Color,
  disabledThumbColor: Color,
  disabledTrackColor: Color,
  interopBackgroundColor: Color,
) {
  val resolvedTestTag = resolveTestTag(testTag, switchTestTag())
  val onCheckedChangeState = rememberUpdatedState(onCheckedChange)
  var pendingNativeState by remember { mutableStateOf<Boolean?>(null) }
  val callbackTarget = remember {
    SwitchValueChangedTarget { isChecked ->
      pendingNativeState = isChecked
      onCheckedChangeState.value(isChecked)
    }
  }

  // Defer UIKitView creation by a few frames so the Compose canvas (including the
  // card background) renders first. Once the card is drawn, the interop container
  // backgrounds (set to the matching color) blend seamlessly.
  var interopReady by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    repeat(3) { withFrameNanos {} }
    interopReady = true
  }

  LaunchedEffect(pendingNativeState, checked) {
    if (pendingNativeState != null && pendingNativeState != checked) {
      // Give the state holder one frame to accept the native toggle. If it doesn't,
      // drop the optimistic native state so UIKit re-syncs to the source of truth.
      withFrameNanos {}
      if (pendingNativeState != null && pendingNativeState != checked) {
        pendingNativeState = null
      }
    }
  }

  Box(
    modifier = modifier
      .resId(resolvedTestTag)
      .semantics {
        role = Role.Switch
        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        if (!enabled || !interactionsEnabled) {
          disabled()
        }
      }
      .size(width = IosSwitchOuterWidth, height = IosSwitchHeight),
    contentAlignment = Alignment.Center
  ) {
    if (interopReady) {
      UIKitView(
        modifier = Modifier.size(width = IosSwitchInteropWidth, height = IosSwitchInteropHeight),
        factory = {
          NativeSwitchHost(
            callbackTarget = callbackTarget,
            interopBackgroundColor = interopBackgroundColor
          )
        },
        update = { hostView ->
          val nativeSwitch = hostView.nativeSwitch
          if (pendingNativeState == checked) {
            pendingNativeState = null
          }

          hostView.updateAppearance(
            enabled = enabled,
            checked = checked,
            checkedThumbColor = checkedThumbColor,
            uncheckedThumbColor = uncheckedThumbColor,
            checkedTrackColor = checkedTrackColor,
            uncheckedTrackColor = uncheckedTrackColor,
            disabledThumbColor = disabledThumbColor,
            disabledTrackColor = disabledTrackColor,
            interopBackgroundColor = interopBackgroundColor
          )

          nativeSwitch.enabled = enabled
          nativeSwitch.userInteractionEnabled = interactionsEnabled
          if (pendingNativeState == null && nativeSwitch.on != checked) {
            nativeSwitch.setOn(checked, animated = false)
          }
        },
        properties = UIKitInteropProperties(
          interactionMode = UIKitInteropInteractionMode.NonCooperative,
          isNativeAccessibilityEnabled = false
        )
      )
    }
  }
}

private class SwitchValueChangedTarget(
  private val onValueChanged: (Boolean) -> Unit,
) : NSObject() {
  @kotlinx.cinterop.ObjCAction
  fun onValueChanged(sender: UISwitch) {
    onValueChanged(sender.on)
  }
}

private class NativeSwitchHost(
  callbackTarget: SwitchValueChangedTarget,
  interopBackgroundColor: Color,
) : UIView(frame = cValue { CGRectZero }) {
  private var lastAppearance: SwitchAppearance? = null
  private var lastInteropBackgroundColor: Color = interopBackgroundColor
  private var hiddenHierarchyAlphaSnapshot: List<ViewAlphaSnapshot> = emptyList()

  val nativeSwitch = UISwitch().apply {
    addTarget(
      target = callbackTarget,
      action = sel_registerName("onValueChanged:"),
      forControlEvents = UIControlEventValueChanged
    )
    opaque = false
    backgroundColor = UIColor.clearColor
    sizeToFit()
  }

  init {
    backgroundColor = interopBackgroundColor.toUIColor()
    opaque = false
    clipsToBounds = false
    addSubview(nativeSwitch)
  }

  override fun didMoveToSuperview() {
    super.didMoveToSuperview()
    if (superview != null) {
      restoreHierarchyAlpha()
      applyInteropBackground(lastInteropBackgroundColor.toUIColor())
    }
  }

  override fun didMoveToWindow() {
    super.didMoveToWindow()
    if (window == null) {
      hideHierarchy()
    } else {
      restoreHierarchyAlpha()
    }
  }

  override fun willMoveToSuperview(newSuperview: UIView?) {
    super.willMoveToSuperview(newSuperview)
    if (newSuperview == null) {
      hideHierarchy()
    }
  }

  override fun layoutSubviews() {
    super.layoutSubviews()

    nativeSwitch.sizeToFit()
    val switchWidth = CGRectGetWidth(nativeSwitch.bounds)
    val switchHeight = CGRectGetHeight(nativeSwitch.bounds)
    nativeSwitch.setFrame(
      CGRectMake(
        x = (CGRectGetWidth(bounds) - switchWidth) / 2.0,
        y = (CGRectGetHeight(bounds) - switchHeight) / 2.0,
        width = switchWidth,
        height = switchHeight
      )
    )
  }

  fun updateAppearance(
    enabled: Boolean,
    checked: Boolean,
    checkedThumbColor: Color,
    uncheckedThumbColor: Color,
    checkedTrackColor: Color,
    uncheckedTrackColor: Color,
    disabledThumbColor: Color,
    disabledTrackColor: Color,
    interopBackgroundColor: Color,
  ) {
    if (interopBackgroundColor != lastInteropBackgroundColor) {
      lastInteropBackgroundColor = interopBackgroundColor
      applyInteropBackground(interopBackgroundColor.toUIColor())
    }

    val appearance = SwitchAppearance(
      onTrackColor = if (enabled) checkedTrackColor else disabledTrackColor,
      offTrackColor = if (enabled) uncheckedTrackColor else disabledTrackColor,
      thumbColor = when {
        !enabled -> disabledThumbColor
        checked -> checkedThumbColor
        else -> uncheckedThumbColor
      }
    )

    if (appearance == lastAppearance) {
      return
    }

    nativeSwitch.onTintColor = appearance.onTrackColor.toUIColor()
    nativeSwitch.tintColor = appearance.offTrackColor.toUIColor()
    nativeSwitch.thumbTintColor = appearance.thumbColor.toUIColor()

    lastAppearance = appearance
  }

  private fun applyInteropBackground(uiColor: UIColor) {
    backgroundColor = uiColor
    opaque = false
    var current: UIView? = superview
    repeat(4) {
      current?.backgroundColor = uiColor
      current?.opaque = false
      current = current?.superview
    }
  }

  private fun hideHierarchy() {
    if (hiddenHierarchyAlphaSnapshot.isEmpty()) {
      hiddenHierarchyAlphaSnapshot = buildList {
        add(ViewAlphaSnapshot(view = this@NativeSwitchHost, alpha = this@NativeSwitchHost.alpha))
        var current: UIView? = superview
        repeat(4) {
          current?.let { add(ViewAlphaSnapshot(view = it, alpha = it.alpha)) }
          current = current?.superview
        }
      }
    }

    hiddenHierarchyAlphaSnapshot.forEach { snapshot ->
      snapshot.view.alpha = 0.0
    }
  }

  private fun restoreHierarchyAlpha() {
    hiddenHierarchyAlphaSnapshot.forEach { snapshot ->
      snapshot.view.alpha = snapshot.alpha
    }
    hiddenHierarchyAlphaSnapshot = emptyList()
  }
}

private data class SwitchAppearance(
  val onTrackColor: Color,
  val offTrackColor: Color,
  val thumbColor: Color,
)

private data class ViewAlphaSnapshot(
  val view: UIView,
  val alpha: Double,
)

private fun Color.toUIColor(): UIColor =
  UIColor.colorWithRed(
    red = red.toDouble(),
    green = green.toDouble(),
    blue = blue.toDouble(),
    alpha = alpha.toDouble()
  )
