package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.*
import build.wallet.statemachine.data.keybox.AccountData.*
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.RotatingAuthKeys
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataProps
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachine

@BitkeyInject(AppScope::class)
class AccountDataStateMachineImpl(
  private val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService,
  private val recoveryStatusService: RecoveryStatusService,
  private val someoneElseIsRecoveringDataStateMachine: SomeoneElseIsRecoveringDataStateMachine,
) : AccountDataStateMachine {
  @Composable
  override fun model(props: AccountDataProps): AccountData {
    return fullAccountDataBasedOnRecovery(
      activeAccount = props.account
    )
  }

  @Composable
  private fun fullAccountDataBasedOnRecovery(activeAccount: FullAccount): AccountData {
    val activeRecovery = rememberActiveRecovery()

    /*
    [shouldShowSomeoneElseIsRecoveringIfPresent] tracks whether we are showing an app-level notice
    of a [SomeoneElseIsRecovering] conflicted recovery state.

    We show this at app-level so that it can be presented no matter where the user
    is currently in the app. Once the state of recovery changes and this recomposes,
    it will take over and be emitted.

    We use this local [shouldShowSomeoneElseIsRecoveringIfPresent] flag to allow the customer to
    close the app-level notice without taking action. The next time they open the
    app, the notice will show.
     */
    var shouldShowSomeoneElseIsRecoveringIfPresent by remember(activeRecovery) { mutableStateOf(true) }

    return when (activeRecovery) {
      is Loading -> CheckingActiveAccountData

      is NoLongerRecovering -> NoLongerRecoveringFullAccountData(activeRecovery.cancelingRecoveryLostFactor)

      is SomeoneElseIsRecovering -> {
        // We only show this when we have an active keybox.
        if (shouldShowSomeoneElseIsRecoveringIfPresent) {
          SomeoneElseIsRecoveringFullAccountData(
            data =
              someoneElseIsRecoveringDataStateMachine.model(
                props =
                  SomeoneElseIsRecoveringDataProps(
                    cancelingRecoveryLostFactor = activeRecovery.cancelingRecoveryLostFactor,
                    onClose = { shouldShowSomeoneElseIsRecoveringIfPresent = false },
                    fullAccountId = activeAccount.accountId
                  )
              ),
            fullAccountId = activeAccount.accountId
          )
        } else {
          // Otherwise, create data solely based on account state.
          hasActiveFullAccountData(activeAccount)
        }
      }

      else -> {
        // Otherwise, create data solely based on Full account state.
        hasActiveFullAccountData(activeAccount)
      }
    }
  }

  /** Manages the state of the case when we have an active Full Account ready to use. */
  @Composable
  private fun hasActiveFullAccountData(account: FullAccount): HasActiveFullAccountData {
    val pendingAuthKeyRotationAttempt by remember {
      fullAccountAuthKeyRotationService.observePendingKeyRotationAttemptUntilNull()
    }.collectAsState(initial = null)

    // TODO: We should probably have a third "None" value, so that we can differentiate between
    //  loading and no pending attempt to mitigate any possible screen flashes.
    pendingAuthKeyRotationAttempt?.let {
      return RotatingAuthKeys(account, pendingAttempt = it)
    }

    return ActiveFullAccountLoadedData(account)
  }

  @Composable
  private fun rememberActiveRecovery(): Recovery {
    return remember { recoveryStatusService.status }
      .collectAsState().value
  }
}
