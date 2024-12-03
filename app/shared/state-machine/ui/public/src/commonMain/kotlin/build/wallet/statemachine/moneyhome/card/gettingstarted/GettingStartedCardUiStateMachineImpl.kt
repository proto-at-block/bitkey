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
import build.wallet.f8e.relationships.Relationships
import build.wallet.feature.flags.MobilePayRevampFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.*
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTaskDao
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayService
import build.wallet.logging.logFailure
import build.wallet.recovery.socrec.SocRecService
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
import kotlinx.coroutines.flow.filterNotNull
import kotlin.time.Duration.Companion.seconds

class GettingStartedCardUiStateMachineImpl(
  private val appFunctionalityService: AppFunctionalityService,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val eventTracker: EventTracker,
  private val bitcoinWalletService: BitcoinWalletService,
  private val mobilePayService: MobilePayService,
  private val socRecService: SocRecService,
  private val mobilePayRevampFeatureFlag: MobilePayRevampFeatureFlag,
) : GettingStartedCardUiStateMachine {
  @Composable
  override fun model(props: GettingStartedCardUiProps): CardModel? {
    val appFunctionalityStatus by remember { appFunctionalityService.status }.collectAsState()
    var uiState by remember { mutableStateOf(UiState(activeTasks = emptyImmutableList())) }

    LaunchedEffect("set-state-based-on-tasks") {
      gettingStartedTaskDao.tasks().collectLatest { activeTasks ->
        uiState = uiState.copy(activeTasks = activeTasks.toImmutableList())
      }
    }

    val relationships by remember { socRecService.socRecRelationships.filterNotNull() }
      .collectAsState(Relationships.EMPTY)
    val trustedContacts by remember(relationships) {
      derivedStateOf {
        relationships.endorsedTrustedContacts + relationships.invitations
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

          InviteTrustedContact -> {
            LaunchedEffect("invite-tc-task", trustedContacts) {
              if (trustedContacts.isNotEmpty()) {
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
              isEnabled = it.isEnabled(appFunctionalityStatus),
              onClick = { it.onClick(props, appFunctionalityStatus) },
              isRevampEnabled = mobilePayRevampFeatureFlag.isEnabled()
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
      AddAdditionalFingerprint -> true
    }

  private fun GettingStartedTask.onClick(
    props: GettingStartedCardUiProps,
    appFunctionalityStatus: AppFunctionalityStatus,
  ) {
    if (isEnabled(appFunctionalityStatus)) {
      when (id) {
        AddBitcoin -> props.onAddBitcoin()
        EnableSpendingLimit -> props.onEnableSpendingLimit()
        InviteTrustedContact -> props.onInviteTrustedContact()
        AddAdditionalFingerprint -> props.onAddAdditionalFingerprint()
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
    val animations: ImmutableList<AnimationSet>? = null,
  )
}
