package build.wallet.statemachine.platform.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import build.wallet.platform.permissions.Permission
import build.wallet.platform.permissions.manifestPermission
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineImpl.UiState.PermissionDeniedUiState
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachineImpl.UiState.RequestingPermissionUiState

actual class PermissionUiStateMachineImpl : PermissionUiStateMachine {
  actual override val isImplemented = true

  @Composable
  actual override fun model(props: PermissionUiProps): BodyModel {
    val activity = LocalContext.current as Activity
    var uiState: UiState by remember {
      mutableStateOf(
        RequestingPermissionUiState(showingSystemPermission = true)
      )
    }

    val requestPermissionLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
          if (granted) {
            props.onGranted()
          } else {
            uiState = calculateDeniedState(activity, props.permission)
          }
        }
      )

    val settingsLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
          uiState = RequestingPermissionUiState(showingSystemPermission = true)
        }
      )

    when (val s = uiState) {
      is RequestingPermissionUiState -> {
        if (s.showingSystemPermission) {
          LaunchedEffect("requesting-permission") {
            requestPermissionLauncher.launch(props.permission.manifestPermission())
          }
        }
      }

      else -> Unit
    }

    return when (val snapshot = uiState) {
      is RequestingPermissionUiState ->
        RequestPermissionBodyModel(
          title = "Camera permission is needed",
          explanation = "In order to access this feature, the camera permission is needed",
          showingSystemPermission = snapshot.showingSystemPermission,
          onBack = props.onExit,
          onRequest = {
            uiState = RequestingPermissionUiState(showingSystemPermission = true)
          }
        )

      PermissionDeniedUiState ->
        AskingToGoToSystemBodyModel(
          title = "Camera permission is needed",
          explanation =
            "In order to access this feature, the camera permission is needed. Please" +
              " go to app settings in order to enable this permission and use this feature",
          onBack = props.onExit,
          onGoToSetting = {
            settingsLauncher.launch(activity.systemSettingsIntent())
          }
        )
    }
  }

  // Produce denied state based on whether we can request again
  private fun calculateDeniedState(
    activity: Activity,
    permission: Permission,
  ): UiState {
    val manifestPermission = permission.manifestPermission()
    return if (
      manifestPermission != null &&
      shouldShowRequestPermissionRationale(activity, manifestPermission)
    ) {
      // App is allowed to request permission.
      RequestingPermissionUiState(showingSystemPermission = false)
    } else {
      PermissionDeniedUiState
    }
  }

  private sealed interface UiState {
    /**
     * Requesting the given permission
     *
     * @property showingSystemPermission - showing the native system permission when true, otherwise
     * an explanation for why its needed
     */
    data class RequestingPermissionUiState(val showingSystemPermission: Boolean) : UiState

    /**
     * Permission denied and unable to show system settings
     */
    data object PermissionDeniedUiState : UiState
  }
}

private fun Activity.systemSettingsIntent() =
  Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", packageName, null)
  )
