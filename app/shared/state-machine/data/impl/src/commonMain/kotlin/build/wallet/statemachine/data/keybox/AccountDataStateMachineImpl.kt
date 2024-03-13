package build.wallet.statemachine.data.keybox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.Keybox
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.db.DbError
import build.wallet.f8e.F8eEnvironment
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.mapResult
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.Loading
import build.wallet.recovery.Recovery.NoActiveRecovery
import build.wallet.recovery.Recovery.NoLongerRecovering
import build.wallet.recovery.Recovery.SomeoneElseIsRecovering
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerDependentRecovery
import build.wallet.recovery.RecoverySyncer
import build.wallet.statemachine.data.account.OnboardConfigData.LoadedOnboardConfigData
import build.wallet.statemachine.data.account.OnboardConfigData.LoadingOnboardConfigData
import build.wallet.statemachine.data.account.create.OnboardConfigDataStateMachine
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoLongerRecoveringFullAccountData
import build.wallet.statemachine.data.keybox.AccountData.SomeoneElseIsRecoveringFullAccountData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringDataStateMachine
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringDataStateMachineDataProps
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataProps
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringDataStateMachine
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration

class AccountDataStateMachineImpl(
  private val hasActiveFullAccountDataStateMachine: HasActiveFullAccountDataStateMachine,
  private val hasActiveLiteAccountDataStateMachine: HasActiveLiteAccountDataStateMachine,
  private val noActiveAccountDataStateMachine: NoActiveAccountDataStateMachine,
  private val accountRepository: AccountRepository,
  private val recoverySyncer: RecoverySyncer,
  private val noLongerRecoveringDataStateMachine: NoLongerRecoveringDataStateMachine,
  private val someoneElseIsRecoveringDataStateMachine: SomeoneElseIsRecoveringDataStateMachine,
  private val recoverySyncFrequency: Duration,
  private val onboardConfigDataStateMachine: OnboardConfigDataStateMachine,
) : AccountDataStateMachine {
  @Composable
  override fun model(props: AccountDataProps): AccountData {
    val activeAccountResult = rememberActiveAccount()
    val activeRecoveryResult = rememberActiveRecovery()

    val onboardConfigData =
      when (val onboardConfigData = onboardConfigDataStateMachine.model(Unit)) {
        // If we don't have results yet, we're still checking
        is LoadingOnboardConfigData -> return CheckingActiveAccountData
        is LoadedOnboardConfigData -> onboardConfigData
      }

    return when (activeAccountResult) {
      null ->
        // If we don't have results yet, we're still checking
        CheckingActiveAccountData

      is Ok -> {
        // We have results from the DB for active account,
        when (val account = activeAccountResult.value) {
          is LiteAccount ->
            hasActiveLiteAccountDataStateMachine.model(
              props =
                HasActiveLiteAccountDataProps(
                  account = account,
                  currencyPreferenceData = props.currencyPreferenceData,
                  accountUpgradeOnboardConfigData = onboardConfigData,
                  accountUpgradeTemplateFullAccountConfigData = props.templateFullAccountConfigData
                )
            )

          is FullAccount? -> {
            // now get the active recovery from the DB
            when (activeRecoveryResult) {
              is Ok -> {
                maybePollRecoveryStatus(
                  templateFullAccountConfigData = props.templateFullAccountConfigData,
                  activeKeybox = account?.keybox,
                  activeRecovery = activeRecoveryResult.value
                )

                // We have results from DB for both keybox and recovery.
                // First, try to create [KeyboxData] based on recovery state.
                fullAccountDataBasedOnRecovery(
                  props = props,
                  activeAccount = account,
                  activeRecovery = activeRecoveryResult.value,
                  onboardConfigData = onboardConfigData
                )
              }

              is Err -> {
                maybePollRecoveryStatus(
                  templateFullAccountConfigData = props.templateFullAccountConfigData,
                  activeKeybox = account?.keybox,
                  activeRecovery = NoActiveRecovery
                )
                NoActiveAccountData(
                  templateFullAccountConfigData = props.templateFullAccountConfigData,
                  activeRecovery = null,
                  currencyPreferenceData = props.currencyPreferenceData,
                  onboardConfigData = onboardConfigData
                )
              }
            }
          }

          else -> {
            NoActiveAccountData(
              templateFullAccountConfigData = props.templateFullAccountConfigData,
              activeRecovery = null,
              currencyPreferenceData = props.currencyPreferenceData,
              onboardConfigData = onboardConfigData
            )
          }
        }
      }

      is Err ->
        NoActiveAccountData(
          templateFullAccountConfigData = props.templateFullAccountConfigData,
          activeRecovery = null,
          currencyPreferenceData = props.currencyPreferenceData,
          onboardConfigData = onboardConfigData
        )
    }
  }

  @Composable
  private fun fullAccountDataBasedOnRecovery(
    props: AccountDataProps,
    activeAccount: FullAccount?,
    activeRecovery: Recovery,
    onboardConfigData: LoadedOnboardConfigData,
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

      is NoLongerRecovering -> {
        NoLongerRecoveringFullAccountData(
          data =
            noLongerRecoveringDataStateMachine.model(
              NoLongerRecoveringDataStateMachineDataProps(
                activeRecovery.cancelingRecoveryLostFactor
              )
            )
        )
      }

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
            props = props,
            activeAccount = activeAccount,
            activeRecovery = activeRecovery,
            onboardConfigData = onboardConfigData
          )
        }
      }

      else -> {
        // Otherwise, create [KeyboxData] solely based on Full account state.
        accountDataBasedOnAccount(
          props = props,
          activeAccount = activeAccount,
          activeRecovery = activeRecovery,
          onboardConfigData = onboardConfigData
        )
      }
    }
  }

  @Composable
  private fun accountDataBasedOnAccount(
    props: AccountDataProps,
    activeAccount: FullAccount?,
    activeRecovery: Recovery,
    onboardConfigData: LoadedOnboardConfigData,
  ): AccountData {
    return when (activeAccount) {
      null -> {
        NoActiveAccountData(
          templateFullAccountConfigData = props.templateFullAccountConfigData,
          activeRecovery = activeRecovery as? StillRecovering,
          currencyPreferenceData = props.currencyPreferenceData,
          onboardConfigData = onboardConfigData
        )
      }

      else -> {
        val hardwareRecovery =
          (activeRecovery as? StillRecovering)?.let { stillRecovering ->
            when (stillRecovering.factorToRecover) {
              App -> {
                // TODO(W-4300) remove this hack to prevent app recovery from getting through.
                log(LogLevel.Error) { "Unexpected app recovery due to data syncing issue W-4300" }
                null
              }

              Hardware -> stillRecovering
            }
          }

        hasActiveFullAccountDataStateMachine.model(
          HasActiveFullAccountDataProps(
            account = activeAccount,
            hardwareRecovery = hardwareRecovery,
            currencyPreferenceData = props.currencyPreferenceData
          )
        )
      }
    }
  }

  @Composable
  private fun rememberActiveAccount(): Result<Account?, Error>? {
    return remember {
      accountRepository.accountStatus()
        .mapResult { (it as? AccountStatus.ActiveAccount)?.account }
        .distinctUntilChanged()
    }.collectAsState(null).value
  }

  @Composable
  private fun rememberActiveRecovery(): Result<Recovery, DbError> {
    return remember { recoverySyncer.recoveryStatus() }
      .collectAsState(Ok(Loading)).value
  }

  @Composable
  private fun NoActiveAccountData(
    templateFullAccountConfigData: LoadedTemplateFullAccountConfigData,
    activeRecovery: StillRecovering?,
    currencyPreferenceData: CurrencyPreferenceData,
    onboardConfigData: LoadedOnboardConfigData,
  ): AccountData {
    val scope = rememberStableCoroutineScope()
    return noActiveAccountDataStateMachine.model(
      NoActiveAccountDataProps(
        templateFullAccountConfigData = templateFullAccountConfigData,
        existingRecovery = activeRecovery,
        currencyPreferenceData = currencyPreferenceData,
        newAccountOnboardConfigData = onboardConfigData,
        onAccountCreated = { account ->
          scope.launch {
            accountRepository.setActiveAccount(account)
          }
        }
      )
    )
  }

  @Composable
  private fun maybePollRecoveryStatus(
    templateFullAccountConfigData: LoadedTemplateFullAccountConfigData,
    activeKeybox: Keybox?,
    activeRecovery: Recovery,
  ) {
    // We always poll for recovery status when there's an active keybox, in case someone else stole
    // your Bitkey.
    if (activeKeybox != null) {
      pollRecoveryStatus(activeKeybox.fullAccountId, activeKeybox.config.f8eEnvironment)
      return
    }

    // We also poll for recovery status if there's an active recovery, and it hasn't progressed
    // into ServerIndependentRecovery yet, in case there's a contest.
    when (activeRecovery) {
      is ServerDependentRecovery ->
        pollRecoveryStatus(
          fullAccountId = activeRecovery.serverRecovery.fullAccountId,
          f8eEnvironment = templateFullAccountConfigData.config.f8eEnvironment
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
