package build.wallet.statemachine.dev.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.debug.NetworkingDebugService
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle.DIVIDER
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class NetworkingDebugConfigPickerUiStateMachineImpl(
  private val networkingDebugService: NetworkingDebugService,
) : NetworkingDebugConfigPickerUiStateMachine {
  @Composable
  override fun model(props: NetworkingDebugConfigProps): BodyModel {
    val networkingDebugConfig = networkingDebugService.config.collectAsState()
    val scope = rememberStableCoroutineScope()

    return NetworkingDebugConfigPickerBodyModel(
      failF8eRequests = networkingDebugConfig.value.failF8eRequests,
      onFailF8eRequestsChanged = { failF8eRequests ->
        scope.launch {
          networkingDebugService.setFailF8eRequests(failF8eRequests)
        }
      },
      onBack = props.onExit
    )
  }
}

private object NetworkingDebugConfigPickerScreenId : EventTrackerScreenId {
  override val name: String
    get() = "NetworkingDebugConfigPicker"
}

private data class NetworkingDebugConfigPickerBodyModel(
  val failF8eRequests: Boolean,
  val onFailF8eRequestsChanged: (Boolean) -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = NetworkingDebugConfigPickerScreenId,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = null,
    mainContentList =
      immutableListOf(
        ListGroup(
          ListGroupModel(
            style = DIVIDER,
            items =
              immutableListOf(
                ListItemModel(
                  title = "Fail F8e Requests",
                  secondaryText =
                    "If enabled, all F8e requests will fail. This option is helpful for " +
                      "testing app behavior when Block is down.",
                  trailingAccessory =
                    SwitchAccessory(
                      model =
                        SwitchModel(
                          checked = failF8eRequests,
                          testTag = "networking-debug-fail-f8e-requests-toggle",
                          onCheckedChange = onFailF8eRequestsChanged
                        )
                    )
                )
              )
          )
        )
      ),
    primaryButton = null,
    eventTrackerShouldTrack = false
  )
