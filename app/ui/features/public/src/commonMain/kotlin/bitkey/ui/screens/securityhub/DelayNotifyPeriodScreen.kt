package bitkey.ui.screens.securityhub

import androidx.compose.runtime.*
import bitkey.securitycenter.DelayNotifyConfigurationService
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.bitkey.account.FullAccount
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList

data class DelayNotifyPeriodScreen(
  val account: FullAccount,
  override val origin: Screen?,
) : Screen

@BitkeyInject(ActivityScope::class)
class DelayNotifyPeriodPresenter(
  private val delayNotifyConfigurationService: DelayNotifyConfigurationService,
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
) : ScreenPresenter<DelayNotifyPeriodScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: DelayNotifyPeriodScreen,
  ): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.ViewingCurrent) }

    val periodDays: Int? by remember(screen.account) {
      delayNotifyConfigurationService.delayNotifyPeriod(screen.account)
    }.collectAsState(initial = null)

    val onBack: () -> Unit = {
      if (screen.origin != null) {
        navigator.goTo(screen.origin)
      } else {
        navigator.exit()
      }
    }

    val currentPeriodDays = periodDays
    return when {
      currentPeriodDays == null -> {
        LoadingBodyModel(
          id = null,
          title = "Loading security waiting period…",
          onBack = onBack
        ).asRootScreen()
      }

      else -> {
        when (val state = uiState) {
      State.ViewingCurrent -> delayNotifyPeriodOverviewModel(
        currentPeriodDays = currentPeriodDays,
        onBack = onBack,
        onChangePeriod = { uiState = State.ChoosingPeriod }
      ).asRootScreen()

      State.ChoosingPeriod -> delayNotifyPeriodOverviewModel(
        currentPeriodDays = currentPeriodDays,
        onBack = onBack,
        onChangePeriod = { uiState = State.ChoosingPeriod }
      ).asRootScreen(
        bottomSheetModel = choosePeriodSheet(
          currentPeriodDays = currentPeriodDays,
          onClose = { uiState = State.ViewingCurrent },
          onSelect = { days ->
            if (days == currentPeriodDays) {
              uiState = State.ViewingCurrent
            } else {
              uiState = State.HardwareConfirmation(selectedDays = days)
            }
          }
        )
      )

      is State.HardwareConfirmation -> hardwareAuthUiStateMachine.model(
        props = HardwareAuthUiProps(
          account = screen.account,
          actionProofType = ActionProofType.SetDelayNotifyPeriod(
            periodDays = state.selectedDays
          ),
          segment = DelayNotifyPeriodAppSegment,
          actionDescription = "Setting delay notify period to ${state.selectedDays} days",
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          onSuccess = { proof ->
            uiState = State.Updating(
              selectedDays = state.selectedDays,
              proof = proof
            )
          },
          onBack = { uiState = State.ViewingCurrent }
        )
      )

      is State.Updating -> {
        LaunchedEffect("update-delay-period") {
          delayNotifyConfigurationService.setDelayNotifyPeriod(
            account = screen.account,
            delayPeriodDays = state.selectedDays,
            proof = state.proof,
          )
            .onSuccess { uiState = State.ViewingCurrent }
            .onFailure { uiState = State.UpdateError(selectedDays = state.selectedDays) }
        }
        LoadingBodyModel(
          id = null,
          title = "Updating security waiting period…",
          onBack = {}
        ).asRootScreen()
      }

          is State.UpdateError -> updatingErrorModel(
            onRetry = {
              uiState = State.HardwareConfirmation(selectedDays = state.selectedDays)
            },
            onBack = { uiState = State.ViewingCurrent }
          ).asRootScreen()
        }
      }
    }
  }
}

private fun delayNotifyPeriodOverviewModel(
  currentPeriodDays: Int,
  onBack: () -> Unit,
  onChangePeriod: () -> Unit,
): BodyModel =
  DelayNotifyPeriodOverviewBodyModel(
    currentPeriodDays = currentPeriodDays,
    onBack = onBack,
    onChangePeriod = onChangePeriod
  )

private data class DelayNotifyPeriodOverviewBodyModel(
  val currentPeriodDays: Int,
  override val onBack: () -> Unit,
  val onChangePeriod: () -> Unit,
) : FormBodyModel(
    id = SecurityHubEventTrackerScreenId.SECURITY_HUB_DELAY_NOTIFY_PERIOD,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      headline = "Security waiting period",
      subline = "This is how long wallet recovery and fingerprint reset take to complete. " +
        "A longer period gives you more time to detect and cancel unauthorized attempts, " +
        "but it also means a longer wait when you recover your wallet or reset fingerprints " +
        "yourself."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          items = immutableListOf(
            ListItemModel(
              title = "Current period",
              sideText = "$currentPeriodDays days",
              trailingAccessory = ListItemAccessory.drillIcon(),
              onClick = onChangePeriod
            )
          ),
          style = ListGroupStyle.DIVIDER
        )
      )
    ),
    primaryButton = null,
    onBack = onBack
  )

private fun choosePeriodSheet(
  currentPeriodDays: Int,
  onClose: () -> Unit,
  onSelect: (Int) -> Unit,
): SheetModel {
  val options = listOf(7, 14, 30)
  return SheetModel(
    onClosed = onClose,
    body = ChoosePeriodSheetBodyModel(
      options = options,
      currentPeriodDays = currentPeriodDays,
      onSelect = onSelect,
      onBack = onClose
    )
  )
}

private data class ChoosePeriodSheetBodyModel(
  val options: List<Int>,
  val currentPeriodDays: Int,
  val onSelect: (Int) -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = null,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Choose security waiting period",
      subline = "Your Bitkey device is required to confirm this change."
    ),
    renderContext = RenderContext.Sheet,
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          items = options.map { days ->
            ListItemModel(
              title = "$days days",
              trailingAccessory = if (days == currentPeriodDays) {
                ListItemAccessory.IconAccessory(
                  model = IconModel(
                    icon = Icon.SmallIconCheckFilled,
                    iconSize = IconSize.Small
                  )
                )
              } else {
                null
              },
              onClick = { onSelect(days) }
            )
          }.toImmutableList(),
          style = ListGroupStyle.DIVIDER
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Cancel",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = SheetClosingClick { onBack() }
    ),
    onBack = onBack
  )

private fun updatingErrorModel(
  onRetry: () -> Unit,
  onBack: () -> Unit,
): BodyModel =
  object : FormBodyModel(
    id = null,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "We couldn't update your security waiting period",
      subline = "Please try again."
    ),
    primaryButton = ButtonModel(
      text = "Retry",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onRetry)
    ),
    secondaryButton = ButtonModel(
      text = "Back",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = StandardClick(onBack)
    ),
    onBack = onBack
  ) {}

private object DelayNotifyPeriodAppSegment : AppSegment {
  override val id: String = "DelayNotifyPeriod"
}

private sealed interface State {
  data object ViewingCurrent : State
  data object ChoosingPeriod : State
  data class HardwareConfirmation(val selectedDays: Int) : State
  data class Updating(
    val selectedDays: Int,
    val proof: PrivilegedActionProof?,
  ) : State
  data class UpdateError(val selectedDays: Int) : State
}
