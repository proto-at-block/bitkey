package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.*
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.Keybox
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.debug.DebugOptions
import build.wallet.debug.DebugOptionsService
import build.wallet.f8e.F8eEnvironment
import build.wallet.logging.*
import build.wallet.mapResult
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.*
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery
import build.wallet.recovery.RecoverySyncer
import build.wallet.statemachine.data.keybox.AccountData.*
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataProps
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachine
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapBoth
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import kotlin.native.HiddenFromObjC
import kotlin.time.Duration

@HiddenFromObjC
class AccountDataStateMachineImpl(
  private val hasActiveFullAccountDataStateMachine: HasActiveFullAccountDataStateMachine,
  private val hasActiveLiteAccountDataStateMachine: HasActiveLiteAccountDataStateMachine,
  private val noActiveAccountDataStateMachine: NoActiveAccountDataStateMachine,
  private val accountService: AccountService,
  private val recoverySyncer: RecoverySyncer,
  private val someoneElseIsRecoveringDataStateMachine: SomeoneElseIsRecoveringDataStateMachine,
  private val recoverySyncFrequency: Duration,
  private val debugOptionsService: DebugOptionsService,
) : AccountDataStateMachine {
  @Composable
  override fun model(props: Unit): AccountData {
    val activeAccountResult = rememberActiveAccount()
    val activeRecoveryResult = rememberActiveRecovery()
    val debugOptions = remember { debugOptionsService.options() }
      .collectAsState(initial = null).value

    if (debugOptions == null) {
      // If we don't have results yet, we're still checking
      return CheckingActiveAccountData
    }

    if (activeAccountResult == null) {
      // If we don't have results yet, we're still checking
      return CheckingActiveAccountData
    }

    return activeAccountResult
      .mapBoth(
        success = {
          // We have results from the DB for active account,
          when (val account = activeAccountResult.value) {
            is LiteAccount ->
              hasActiveLiteAccountDataStateMachine.model(
                props = HasActiveLiteAccountDataProps(account = account)
              )

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
                    debugOptions = debugOptions,
                    activeAccount = account,
                    activeRecovery = activeRecoveryResult.value
                  )
                },
                failure = {
                  maybePollRecoveryStatus(
                    activeKeybox = account?.keybox,
                    activeRecovery = NoActiveRecovery
                  )
                  NoActiveAccountData(
                    debugOptions = debugOptions,
                    activeRecovery = null
                  )
                }
              )
            }

            else -> {
              NoActiveAccountData(
                debugOptions = debugOptions,
                activeRecovery = null
              )
            }
          }
        },
        failure = {
          NoActiveAccountData(
            debugOptions = debugOptions,
            activeRecovery = null
          )
        }
      )
  }

  @Composable
  private fun fullAccountDataBasedOnRecovery(
    debugOptions: DebugOptions,
    activeAccount: FullAccount?,
    activeRecovery: Recovery,
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
                    f8eEnvironment = activeAccount.config.f8eEnvironment,
                    fullAccountId = activeAccount.accountId
                  )
              ),
            fullAccountConfig = activeAccount.keybox.config,
            fullAccountId = activeAccount.accountId
          )
        } else {
          // Otherwise, create [KeyboxData] solely based on keybox state.
          accountDataBasedOnAccount(
            debugOptions = debugOptions,
            activeAccount = activeAccount,
            activeRecovery = activeRecovery
          )
        }
      }

      else -> {
        // Otherwise, create [KeyboxData] solely based on Full account state.
        accountDataBasedOnAccount(
          debugOptions = debugOptions,
          activeAccount = activeAccount,
          activeRecovery = activeRecovery
        )
      }
    }
  }

  @Composable
  private fun accountDataBasedOnAccount(
    debugOptions: DebugOptions,
    activeAccount: FullAccount?,
    activeRecovery: Recovery,
  ): AccountData {
    return when (activeAccount) {
      null -> {
        NoActiveAccountData(
          debugOptions = debugOptions,
          activeRecovery = activeRecovery as? StillRecovering
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

        hasActiveFullAccountDataStateMachine.model(
          HasActiveFullAccountDataProps(
            account = activeAccount,
            hardwareRecovery = hardwareRecovery
          )
        )
      }
    }
  }

  @Composable
  private fun rememberActiveAccount(): Result<Account?, Error>? {
    return remember {
      accountService.accountStatus()
        .mapResult { (it as? AccountStatus.ActiveAccount)?.account }
        // Software accounts do not rely on the account DSM; filter them out so that this DSM
        // does not reset app state when a software account is activated.
        .filterNot { it.get() is SoftwareAccount }
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
    debugOptions: DebugOptions,
    activeRecovery: StillRecovering?,
  ): AccountData {
    val scope = rememberStableCoroutineScope()
    return noActiveAccountDataStateMachine.model(
      NoActiveAccountDataProps(
        debugOptions = debugOptions,
        existingRecovery = activeRecovery,
        onAccountCreated = { account ->
          scope.launch {
            accountService.setActiveAccount(account)
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
      pollRecoveryStatus(activeKeybox.fullAccountId, activeKeybox.config.f8eEnvironment)
      return
    }

    val debugOptions = remember { debugOptionsService.options() }
      .collectAsState(initial = null)
      .value ?: return

    // We also poll for recovery status if there's an active recovery, and it hasn't progressed
    // into ServerIndependentRecovery yet, in case there's a contest.
    when (activeRecovery) {
      is ServerDependentRecovery ->
        pollRecoveryStatus(
          fullAccountId = activeRecovery.serverRecovery.fullAccountId,
          f8eEnvironment = debugOptions.f8eEnvironment
        )

      else -> {}
    }
  }

  @Composable
  private fun pollRecoveryStatus(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ) {
    LaunchedEffect("recovery-syncer-sync", fullAccountId, f8eEnvironment) {
      recoverySyncer.launchSync(
        scope = this,
        syncFrequency = recoverySyncFrequency,
        fullAccountId = fullAccountId,
        f8eEnvironment = f8eEnvironment
      )
    }
  }
}
