package build.wallet.statemachine.moneyhome.card.gettingstarted

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_GETTINGSTARTED_COMPLETED
import build.wallet.analytics.v1.Action.ACTION_APP_WALLET_FUNDED
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.AddBitcoin
import build.wallet.home.GettingStartedTask.TaskId.EnableSpendingLimit
import build.wallet.home.GettingStartedTask.TaskId.InviteTrustedContact
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTaskDao
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.statemachine.data.mobilepay.MobilePayData
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Height
import build.wallet.statemachine.moneyhome.card.CardModel.AnimationSet.Animation.Scale
import build.wallet.statemachine.status.AppFunctionalityStatusAlertModel
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.seconds

class GettingStartedCardUiStateMachineImpl(
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val eventTracker: EventTracker,
) : GettingStartedCardUiStateMachine {
  @Composable
  override fun model(props: GettingStartedCardUiProps): CardModel? {
    var uiState by remember { mutableStateOf(UiState(activeTasks = emptyImmutableList())) }

    LaunchedEffect("set-state-based-on-tasks") {
      gettingStartedTaskDao.tasks().collectLatest { activeTasks ->
        uiState = uiState.copy(activeTasks = activeTasks.toImmutableList())
      }
    }

    // Set up listeners for tasks
    if (uiState.activeTasks.isNotEmpty()) {
      for (task in uiState.activeTasks) {
        when (task.id) {
          AddBitcoin -> {
            val transactions = props.accountData.transactionsData.transactions
            LaunchedEffect("add-bitcoin-task", transactions) {
              if (transactions.isNotEmpty()) {
                gettingStartedTaskDao.updateTask(AddBitcoin, Complete)
                eventTracker.track(ACTION_APP_WALLET_FUNDED)
              }
            }
          }

          EnableSpendingLimit -> {
            LaunchedEffect("enable-spending-limit-task", props.accountData.mobilePayData) {
              if (props.accountData.mobilePayData is MobilePayData.MobilePayEnabledData) {
                gettingStartedTaskDao.updateTask(EnableSpendingLimit, Complete)
              }
            }
          }

          InviteTrustedContact -> {
            LaunchedEffect("invite-tc-task", props.trustedContacts) {
              if (props.trustedContacts.isNotEmpty()) {
                gettingStartedTaskDao.updateTask(InviteTrustedContact, Complete)
              }
            }
          }

          else -> Unit
        }
      }
    }

    // Clear tasks when all are complete
    if (uiState.activeTasks.isNotEmpty() && uiState.activeTasks.all { it.state == Complete }) {
      LaunchedEffect("clear-tasks") {
        log { "Animating getting started tasks out" }
        // First, pause for 1 second to show the completed state.
        delay(1.seconds)
        // Then, animate the card.
        val emphasisAnimationDurationInSeconds = 0.55
        val disappearAnimationDurationInSeconds = 0.55
        uiState =
          uiState.copy(
            animations =
              immutableListOf(
                AnimationSet(
                  animations = setOf(Scale(1.05f)),
                  durationInSeconds = emphasisAnimationDurationInSeconds
                ),
                AnimationSet(
                  animations =
                    setOf(
                      Scale(0.001f), // iOS can't animate all the way to 0
                      Height(0f)
                    ),
                  durationInSeconds = disappearAnimationDurationInSeconds
                )
              )
          )
        // Finally, clear the cards, making sure to first give enough
        // time for animations to complete
        val totalAnimationDurationInSeconds =
          emphasisAnimationDurationInSeconds + disappearAnimationDurationInSeconds
        delay(totalAnimationDurationInSeconds.seconds)
        log { "Clearing getting started tasks" }
        gettingStartedTaskDao.clearTasks()
          .onSuccess {
            eventTracker.track(ACTION_APP_GETTINGSTARTED_COMPLETED)
          }
          .logFailure { "Error clearing onboarding tasks table" }
      }
    }

    return if (uiState.activeTasks.isNotEmpty()) {
      GettingStartedCardModel(
        animations = uiState.animations,
        taskModels =
          uiState.activeTasks.map {
            GettingStartedTaskRowModel(
              task = it,
              isEnabled = it.isEnabled(props.appFunctionalityStatus),
              onClick = { it.onClick(props) }
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
      InviteTrustedContact ->
        appFunctionalityStatus.featureStates.securityAndRecovery == Available
    }

  private fun GettingStartedTask.onClick(props: GettingStartedCardUiProps) {
    if (isEnabled(props.appFunctionalityStatus)) {
      when (id) {
        AddBitcoin -> props.onAddBitcoin()
        EnableSpendingLimit -> props.onEnableSpendingLimit()
        InviteTrustedContact -> props.onInviteTrustedContact()
      }
    } else {
      when (props.appFunctionalityStatus) {
        is AppFunctionalityStatus.FullFunctionality -> Unit // Unexpected
        is AppFunctionalityStatus.LimitedFunctionality ->
          props.onShowAlert(
            AppFunctionalityStatusAlertModel(
              status = props.appFunctionalityStatus,
              onDismiss = props.onDismissAlert
            )
          )
      }
    }
  }

  private data class UiState(
    val activeTasks: ImmutableList<GettingStartedTask>,
    val animations: ImmutableList<AnimationSet>? = null,
  )
}
