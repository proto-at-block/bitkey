package build.wallet.integration.statemachine.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.integration.statemachine.recovery.RecoveryTestingTrackerScreenId.RECOVERY_COMPLETED
import build.wallet.integration.statemachine.recovery.RecoveryTestingTrackerScreenId.RECOVERY_NOT_STARTED
import build.wallet.recovery.Recovery.Loading
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.RecoverySyncer
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.RecoveringAccountData
import build.wallet.statemachine.data.keybox.AccountDataStateMachineImpl
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachineImpl
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow

/**
 * This state machine is a simplified root state machine for recovery. It expects the keybox to
 * be empty and will trigger recovery immediately after the embedded KeyboxDataStateMachine
 * recognizes that the keybox is empty.
 *
 * Recovery functional tests should instantiate this state machine and use the state machine tester
 * to run integration tests.
 *
 *   recoveryStateMachine.test(
 *     props = fullAccountConfig,
 *     testTimeout = 20.seconds,
 *     turbineTimeout = 5.seconds
 *   ) {
 *     awaitUntilScreenWithBody<LoadingScreenModel>(CLOUD_SIGN_IN_LOADING)
 *     ... assertions ...
 *     // Wait until recovery completed
 *     awaitUntilScreenWithBody<SuccessScreenModel>(RECOVERY_COMPLETED)
 *   }
 */
class RecoveryTestingStateMachine(
  val dsm: AccountDataStateMachineImpl,
  val usm: LostAppRecoveryUiStateMachineImpl,
  val recoverySyncer: RecoverySyncer,
  val accountService: AccountService,
) : StateMachine<Unit, ScreenModel> {
  @Composable
  override fun model(props: Unit): ScreenModel {
    val accountStatus =
      remember { accountService.accountStatus() }.collectAsState(null)
        .value?.getOrThrow()

    val activeRecovery =
      remember { recoverySyncer.recoveryStatus() }.collectAsState(Ok(Loading))
        .value.getOrThrow()
    if (accountStatus is ActiveAccount && accountStatus.account is FullAccount && activeRecovery == NoActiveRecovery) {
      return preStartOrPostRecoveryCompletionScreen(RECOVERY_COMPLETED)
    }
    val data = dsm.model(Unit)
    return when (data) {
      is GettingStartedData -> {
        data.startRecovery()
        preStartOrPostRecoveryCompletionScreen(RECOVERY_NOT_STARTED)
      }
      is AccountData.NoActiveAccountData.CheckingCloudBackupData -> {
        data.onStartLostAppRecovery()
        preStartOrPostRecoveryCompletionScreen(RECOVERY_NOT_STARTED)
      }
      is AccountData.CheckingActiveAccountData -> {
        preStartOrPostRecoveryCompletionScreen(RECOVERY_NOT_STARTED)
      }

      is RecoveringAccountData ->
        usm.model(
          LostAppRecoveryUiProps(
            recoveryData = data.lostAppRecoveryData,
            debugOptions = data.debugOptions
          )
        )

      else -> preStartOrPostRecoveryCompletionScreen(RECOVERY_NOT_STARTED)
    }
  }
}

enum class RecoveryTestingTrackerScreenId : EventTrackerScreenId {
  /**
   * The state machine has not yet entered recovery.
   */
  RECOVERY_NOT_STARTED,

  /**
   * The state machine has exited recovery.
   */
  RECOVERY_COMPLETED,

  /**
   * The state machine has aborted recovery.
   */
  RECOVERY_ABORTED,
}

internal fun preStartOrPostRecoveryCompletionScreen(
  id: RecoveryTestingTrackerScreenId,
): ScreenModel =
  ScreenModel(
    SuccessBodyModel(
      title = "Recovery Success",
      id = id,
      primaryButtonModel = null
    )
  )
