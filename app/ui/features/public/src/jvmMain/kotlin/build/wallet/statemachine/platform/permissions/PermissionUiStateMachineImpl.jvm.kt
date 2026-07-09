package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel

/**
 * Desktop (JVM) dev-mode permission gate.
 *
 * Like iOS, the permission gate is reported as not implemented ([isImplemented] = false). Consumers
 * (e.g. send / Emergency Exit Kit QR entry) short-circuit when this is false and proceed straight to
 * the gated screen, so no system permission prompt is needed on desktop.
 *
 * Should this ever be rendered, [model] grants the permission deterministically rather than throwing
 * a `TODO`, so a desktop flow can never dead-end here.
 *
 * Dev-only: this `actual` lives in jvmMain; Android implements a real permission flow.
 */
@BitkeyInject(ActivityScope::class)
class PermissionUiStateMachineImpl : PermissionUiStateMachine {
  override val isImplemented: Boolean = false

  @Composable
  override fun model(props: PermissionUiProps): BodyModel {
    // Auto-grant on first composition so the flow continues even if rendered.
    LaunchedEffect("desktop-auto-grant-permission") {
      props.onGranted()
    }
    return RequestPermissionBodyModel(
      title = "Permission (desktop dev)",
      explanation = "Permissions are auto-granted on desktop dev builds.",
      showingSystemPermission = false,
      onBack = props.onExit,
      onRequest = props.onGranted
    )
  }
}
