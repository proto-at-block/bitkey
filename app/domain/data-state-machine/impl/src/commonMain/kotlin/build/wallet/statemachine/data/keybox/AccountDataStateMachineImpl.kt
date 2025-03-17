package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.*
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.Keybox
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.mapResult
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.*
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery
import build.wallet.recovery.RecoverySyncFrequency
import build.wallet.recovery.RecoverySyncer
import build.wallet.statemachine.data.keybox.AccountData.*
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.RotatingAuthKeys
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataProps
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataStateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryProps
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapBoth
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class AccountDataStateMachineImpl(
  private val lostHardwareRecoveryDataStateMachine: LostHardwareRecoveryDataStateMachine,
  private val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService,
  private val noActiveAccountDataStateMachine: NoActiveAccountDataStateMachine,
  private val accountService: AccountService,
  private val recoverySyncer: RecoverySyncer,
  private val someoneElseIsRecoveringDataStateMachine: SomeoneElseIsRecoveringDataStateMachine,
  private val recoverySyncFrequency: RecoverySyncFrequency,
) : AccountDataStateMachine {
  @Composable
  override fun model(props: AccountDataProps): AccountData {
    val activeAccountResult = rememberActiveAccount()
    val activeRecoveryResult = rememberActiveRecovery()
    return if (activeAccountResult == null) {
      // If we don't have results yet, we're still checking
      CheckingActiveAccountData
    } else {
      activeAccountResult
        .mapBoth(
          success = {
            // We have results from the DB for active account,
            when (val account = activeAccountResult.value) {
              is FullAccount? -> {
                // now get the active recovery from the DB
                activeAccountResult.mapBoth(
                  success = {
                    maybePollRecoveryStatus(
                      activeKeybox = account?.keybox,
                      activeRecovery = activeRecoveryResult.value
                    )

                    // We have results from DB for both keybox and recovery.
                    // First, try to create [KeyboxData] based on recovery state.
                    fullAccountDataBasedOnRecovery(
                      activeAccount = account,
                      activeRecovery = activeRecoveryResult.value,
                      onLiteAccountCreated = props.onLiteAccountCreated
                    )
                  },
                  failure = {
                    maybePollRecoveryStatus(
                      activeKeybox = account?.keybox,
                      activeRecovery = NoActiveRecovery
                    )
                    NoActiveAccountData(
                      activeRecovery = null,
                      onLiteAccountCreated = props.onLiteAccountCreated
                    )
                  }
                )
              }

              else -> {
                NoActiveAccountData(
                  activeRecovery = null,
                  onLiteAccountCreated = props.onLiteAccountCreated
                )
              }
            }
          },
          failure = {
            NoActiveAccountData(
              activeRecovery = null,
              onLiteAccountCreated = props.onLiteAccountCreated
            )
          }
        )
    }
  }

  @Composable
  private fun fullAccountDataBasedOnRecovery(
    activeAccount: FullAccount?,
    activeRecovery: Recovery,
    onLiteAccountCreated: (LiteAccount) -> Unit,
  ): AccountData {
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
    var shouldShowSomeoneElseIsRecoveringIfPresent by remember { mutableStateOf(true) }

    return when (activeRecovery) {
      is Loading -> CheckingActiveAccountData

      is NoLongerRecovering -> NoLongerRecoveringFullAccountData(activeRecovery.cancelingRecoveryLostFactor)

      is SomeoneElseIsRecovering -> {
        // We only show this when we have an active keybox.
        if (shouldShowSomeoneElseIsRecoveringIfPresent && activeAccount != null) {
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
          // Otherwise, create [KeyboxData] solely based on keybox state.
          accountDataBasedOnAccount(
            activeAccount = activeAccount,
            activeRecovery = activeRecovery,
            onLiteAccountCreated = onLiteAccountCreated
          )
        }
      }

      else -> {
        // Otherwise, create [KeyboxData] solely based on Full account state.
        accountDataBasedOnAccount(
          activeAccount = activeAccount,
          activeRecovery = activeRecovery,
          onLiteAccountCreated = onLiteAccountCreated
        )
      }
    }
  }

  @Composable
  private fun accountDataBasedOnAccount(
    activeAccount: FullAccount?,
    activeRecovery: Recovery,
    onLiteAccountCreated: (LiteAccount) -> Unit,
  ): AccountData {
    return when (activeAccount) {
      null -> {
        NoActiveAccountData(
          activeRecovery = activeRecovery as? StillRecovering,
          onLiteAccountCreated = onLiteAccountCreated
        )
      }

      else -> {
        val hardwareRecovery =
          (activeRecovery as? StillRecovering)?.let { stillRecovering ->
            when (stillRecovering.factorToRecover) {
              App -> {
                // TODO(W-4300) remove this hack to prevent app recovery from getting through.
                logError { "Unexpected app recovery due to data syncing issue W-4300" }
                null
              }

              Hardware -> stillRecovering
            }
          }

        hasActiveFullAccountData(activeAccount, hardwareRecovery)
      }
    }
  }

  /** Manages the state of the case when we have an active Full Account ready to use. */
  @Composable
  private fun hasActiveFullAccountData(
    account: FullAccount,
    hardwareRecovery: StillRecovering?,
  ): HasActiveFullAccountData {
    hardwareRecovery?.let {
      require(it.factorToRecover == Hardware)
    }
    val pendingAuthKeyRotationAttempt by remember {
      fullAccountAuthKeyRotationService.observePendingKeyRotationAttemptUntilNull()
    }.collectAsState(initial = null)

    // TODO: We should probably have a third "None" value, so that we can differentiate between
    //  loading and no pending attempt to mitigate any possible screen flashes.
    pendingAuthKeyRotationAttempt?.let {
      return RotatingAuthKeys(account, pendingAttempt = it)
    }

    val lostHardwareRecoveryData = lostHardwareRecoveryDataStateMachine
      .model(LostHardwareRecoveryProps(account, hardwareRecovery))

    return ActiveFullAccountLoadedData(account, lostHardwareRecoveryData)
  }

  @Composable
  private fun rememberActiveAccount(): Result<Account?, Error>? {
    return remember {
      accountService.accountStatus()
        .mapResult { (it as? AccountStatus.ActiveAccount)?.account }
        // Software and lite accounts do not rely on the account DSM; filter them out so that this DSM
        // does not reset app state when a software account is activated.
        .filterNot { it.get() is SoftwareAccount || it.get() is LiteAccount }
        .distinctUntilChanged()
    }.collectAsState(null).value
  }

  @Composable
  private fun rememberActiveRecovery(): Result<Recovery, Error> {
    return remember { recoverySyncer.recoveryStatus() }
      .collectAsState(Ok(Loading)).value
  }

  @Composable
  private fun NoActiveAccountData(
    activeRecovery: StillRecovering?,
    onLiteAccountCreated: (LiteAccount) -> Unit,
  ): AccountData {
    val scope = rememberStableCoroutineScope()
    return noActiveAccountDataStateMachine.model(
      NoActiveAccountDataProps(
        existingRecovery = activeRecovery,
        onAccountCreated = { account ->
          scope.launch {
            accountService.setActiveAccount(account)
            if (account is LiteAccount) {
              onLiteAccountCreated(account)
            }
          }
        }
      )
    )
  }

  @Composable
  private fun maybePollRecoveryStatus(
    activeKeybox: Keybox?,
    activeRecovery: Recovery,
  ) {
    // We always poll for recovery status when there's an active keybox, in case someone else stole
    // your Bitkey.
    if (activeKeybox != null) {
      pollRecoveryStatus(activeKeybox.fullAccountId)
      return
    }

    // We also poll for recovery status if there's an active recovery, and it hasn't progressed
    // into ServerIndependentRecovery yet, in case there's a contest.
    when (activeRecovery) {
      is ServerDependentRecovery -> pollRecoveryStatus(activeRecovery.serverRecovery.fullAccountId)
      else -> {}
    }
  }

  @Composable
  private fun pollRecoveryStatus(fullAccountId: FullAccountId) {
    LaunchedEffect("recovery-syncer-sync", fullAccountId) {
      recoverySyncer.launchSync(
        scope = this,
        syncFrequency = recoverySyncFrequency.value,
        fullAccountId = fullAccountId
      )
    }
  }
}
