package build.wallet.statemachine.moneyhome.card.gettingstarted

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_GETTINGSTARTED_COMPLETED
import build.wallet.analytics.v1.Action.ACTION_APP_WALLET_FUNDED
import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.AddBitcoin
import build.wallet.home.GettingStartedTask.TaskId.EnableSpendingLimit
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTaskDao
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayService
import build.wallet.logging.logFailure
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(ActivityScope::class)
class GettingStartedCardUiStateMachineImpl(
  private val appFunctionalityService: AppFunctionalityService,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val eventTracker: EventTracker,
  private val bitcoinWalletService: BitcoinWalletService,
  private val mobilePayService: MobilePayService,
) : GettingStartedCardUiStateMachine {
  @Composable
  override fun model(props: GettingStartedCardUiProps): GettingStartedSectionModel? {
    val appFunctionalityStatus by remember { appFunctionalityService.status }.collectAsState()
    var uiState by remember { mutableStateOf(UiState(activeTasks = emptyImmutableList())) }

    LaunchedEffect("set-state-based-on-tasks") {
      gettingStartedTaskDao.tasks().collectLatest { activeTasks ->
        uiState = uiState.copy(activeTasks = activeTasks.toImmutableList())
      }
    }

    val transactionsData = remember { bitcoinWalletService.transactionsData() }
      .collectAsState().value
    val transactions = transactionsData?.transactions ?: immutableListOf()

    val mobilePayData = remember { mobilePayService.mobilePayData }
      .collectAsState()
      .value

    // Set up listeners for tasks
    if (uiState.activeTasks.isNotEmpty()) {
      for (task in uiState.activeTasks) {
        when (task.id) {
          AddBitcoin -> {
            LaunchedEffect("add-bitcoin-task", transactions) {
              if (transactions.isNotEmpty() && task.state != Complete) {
                gettingStartedTaskDao.updateTask(AddBitcoin, Complete)
                eventTracker.track(ACTION_APP_WALLET_FUNDED)
              }
            }
          }

          EnableSpendingLimit -> {
            LaunchedEffect("enable-spending-limit-task", mobilePayData) {
              if (mobilePayData is MobilePayEnabledData) {
                gettingStartedTaskDao.updateTask(EnableSpendingLimit, Complete)
              }
            }
          }
        }
      }
    }

    // Clear tasks when all are complete
    if (uiState.activeTasks.isNotEmpty() && uiState.activeTasks.all { it.state == Complete }) {
      LaunchedEffect("clear-tasks", props.showUpdateFirmwareTile) {
        // Pause briefly to show the completed state before clearing.
        delay(1.seconds)
        gettingStartedTaskDao.clearTasks()
          .onSuccess {
            eventTracker.track(ACTION_APP_GETTINGSTARTED_COMPLETED)
          }
          .logFailure { "Error clearing onboarding tasks table" }
      }
    }

    return if (uiState.activeTasks.isNotEmpty() || props.showUpdateFirmwareTile) {
      GettingStartedCardModel(
        firmwareUpdateTile =
          props.showUpdateFirmwareTile.takeIf { it }
            ?.let { FirmwareUpdateGettingStartedTileModel(onClick = props.onUpdateFirmware) },
        taskModels =
          uiState.activeTasks.map {
            GettingStartedTaskRowModel(
              task = it,
              isEnabled = it.isEnabled(appFunctionalityStatus),
              onClick = { it.onClick(props, appFunctionalityStatus) }
            )
          }.toImmutableList()
      )
    } else {
      null
    }
  }

  private fun GettingStartedTask.isEnabled(appFunctionalityStatus: AppFunctionalityStatus) =
    when (id) {
      AddBitcoin ->
        appFunctionalityStatus.featureStates.deposit == Available
      EnableSpendingLimit ->
        appFunctionalityStatus.featureStates.mobilePay == Available
    }

  private fun GettingStartedTask.onClick(
    props: GettingStartedCardUiProps,
    appFunctionalityStatus: AppFunctionalityStatus,
  ) {
    if (isEnabled(appFunctionalityStatus)) {
      when (id) {
        AddBitcoin -> props.onAddBitcoin()
        EnableSpendingLimit -> props.onEnableSpendingLimit()
      }
    } else {
      when (appFunctionalityStatus) {
        is AppFunctionalityStatus.FullFunctionality -> Unit // Unexpected
        is AppFunctionalityStatus.LimitedFunctionality ->
          props.onShowAlert(
            AppFunctionalityStatusAlertModel(
              status = appFunctionalityStatus,
              onDismiss = props.onDismissAlert
            )
          )
      }
    }
  }

  private data class UiState(
    val activeTasks: ImmutableList<GettingStartedTask>,
  )
}
