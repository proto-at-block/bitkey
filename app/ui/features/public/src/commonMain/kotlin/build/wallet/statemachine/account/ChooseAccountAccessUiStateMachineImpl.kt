package build.wallet.statemachine.account

import androidx.compose.runtime.*
import bitkey.ui.framework.NavigatorPresenter
import bitkey.ui.screens.demo.DemoModeDisabledScreen
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitAssociation.EekBuild
import build.wallet.emergencyexitkit.EmergencyExitKitDataProvider
import build.wallet.feature.flags.OrphanedKeyRecoveryFeatureFlag
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logWarn
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.*
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.recovery.OrphanedKeyDetectionService
import build.wallet.recovery.OrphanedKeyRecoveryService
import build.wallet.recovery.OrphanedKeyRecoveryService.RecoverableAccount
import build.wallet.recovery.OrphanedKeysState
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachineImpl.State.*
import build.wallet.statemachine.account.create.CreateAccountOptionsModel
import build.wallet.statemachine.account.create.CreateSoftwareWalletProps
import build.wallet.statemachine.account.create.CreateSoftwareWalletUiStateMachine
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.keybox.OrphanedKeyRecoveryUiState
import build.wallet.statemachine.dev.DebugMenuScreen
import build.wallet.statemachine.recovery.orphaned.OrphanedAccountSelectionBodyModel
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class ChooseAccountAccessUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val navigatorPresenter: NavigatorPresenter,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val emergencyExitKitDataProvider: EmergencyExitKitDataProvider,
  private val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
  private val createSoftwareWalletUiStateMachine: CreateSoftwareWalletUiStateMachine,
  private val orphanedKeyDetectionService: OrphanedKeyDetectionService,
  private val orphanedKeyRecoveryService: OrphanedKeyRecoveryService,
  private val orphanedKeyRecoveryFeatureFlag: OrphanedKeyRecoveryFeatureFlag,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
  private val keyboxDao: KeyboxDao,
) : ChooseAccountAccessUiStateMachine {
  private companion object {
    const val LOG_TAG = "[OrphanedKeyRecovery]"
  }

  @Composable
  override fun model(props: ChooseAccountAccessUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(ShowingChooseAccountAccess) }

    val isEekBuild = remember { emergencyExitKitDataProvider.getAssociatedEekData() == EekBuild }
    val softwareWalletFlag by remember {
      softwareWalletIsEnabledFeatureFlag.flagValue()
    }.collectAsState()

    val orphanedKeysState by orphanedKeyDetectionService
      .orphanedKeysState()
      .collectAsState()

    val orphanedKeyRecoveryFlag by remember {
      orphanedKeyRecoveryFeatureFlag.flagValue()
    }.collectAsState()

    LaunchedEffect(orphanedKeyRecoveryFlag.value) {
      if (orphanedKeyRecoveryFlag.value) {
        orphanedKeyDetectionService.detect()
      }
    }

    val showOrphanedKeyRecovery = orphanedKeyRecoveryFlag.value &&
      orphanedKeysState is OrphanedKeysState.OrphanedKeysFound

    return when (state) {
      is ShowingCreateAccountOptions -> {
        CreateAccountOptionsModel(
          onBack = { state = ShowingChooseAccountAccess },
          onUseHardwareClick = {
            props.onCreateFullAccount()
          },
          onUseThisDeviceClick = {
            state = CreatingSoftwareWallet
          }
        ).asRootScreen()
      }
      is ShowingChooseAccountAccess -> {
        var alert: ButtonAlertModel? by remember { mutableStateOf(null) }
        ChooseAccountAccessModel(
          onLogoClick = {
            // Only enable the debug menu in non-customer builds
            when (appVariant) {
              Customer -> state = ShowingDemoMode
              Team, Development, Alpha -> state = ShowingDebugMenu
              else -> Unit
            }
          },
          onSetUpNewWalletClick = {
            if (isEekBuild) {
              alert = featureUnavailableForEekAlert(onDismiss = { alert = null })
            } else {
              if (softwareWalletFlag.value) {
                state = ShowingCreateAccountOptions
              } else {
                props.onCreateFullAccount()
              }
            }
          },
          onMoreOptionsClick = { state = ShowingAccountAccessMoreOptions }
        ).asRootFullScreen(
          alertModel = alert
        )
      }

      is ShowingAccountAccessMoreOptions -> {
        if (isEekBuild) {
          EmergencyAccountAccessMoreOptionsFormBodyModel(
            onBack = { state = ShowingChooseAccountAccess },
            onRestoreEmergencyExitKit = props.chooseAccountAccessData.startEmergencyExitRecovery
          ).asRootScreen()
        } else {
          AccountAccessMoreOptionsFormBodyModel(
            onBack = { state = ShowingChooseAccountAccess },
            onRestoreYourWalletClick = props.chooseAccountAccessData.startRecovery,
            onBeTrustedContactClick = {
              props.chooseAccountAccessData.startLiteAccountCreation()
            },
            onRecoverFromOrphanedKeysClick = if (showOrphanedKeyRecovery) {
              { state = DiscoveringOrphanedAccounts }
            } else {
              null
            }
          ).asRootScreen()
        }
      }

      is DiscoveringOrphanedAccounts -> DiscoveringOrphanedAccountsScreen(
        onAccountsDiscovered = { accounts ->
          state = SelectingOrphanedAccount(
            accounts = accounts,
            selectedAccount = if (accounts.size == 1) accounts.first() else null
          )
        },
        onError = { state = ShowingChooseAccountAccess }
      )

      is SelectingOrphanedAccount -> SelectingOrphanedAccountScreen(
        accounts = (state as SelectingOrphanedAccount).accounts,
        initialSelectedAccount = (state as SelectingOrphanedAccount).selectedAccount,
        onRecover = { account -> state = RecoveringFromOrphanedKeys(account) },
        onBack = { state = ShowingChooseAccountAccess }
      )

      is RecoveringFromOrphanedKeys -> RecoveringFromOrphanedKeysScreenModel(
        recoverableAccount = (state as RecoveringFromOrphanedKeys).recoverableAccount,
        onSuccess = { state = ShowingChooseAccountAccess }
      )

      is ShowingBeTrustedContactIntroduction -> {
        BeTrustedContactIntroductionModel(
          onBack = { state = ShowingChooseAccountAccess },
          onContinue = props.chooseAccountAccessData.startLiteAccountCreation,
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        ).asRootScreen()
      }

      is ShowingDebugMenu -> navigatorPresenter.model(
        initialScreen = DebugMenuScreen,
        onExit = { state = ShowingChooseAccountAccess }
      )

      is CreatingSoftwareWallet -> createSoftwareWalletUiStateMachine.model(
        props = CreateSoftwareWalletProps(
          onExit = {
            state = ShowingChooseAccountAccess
          },
          onSuccess = props.onSoftwareWalletCreated
        )
      )

      is ShowingDemoMode -> navigatorPresenter.model(
        initialScreen = DemoModeDisabledScreen,
        onExit = { state = ShowingChooseAccountAccess }
      )
    }
  }

  @Composable
  private fun DiscoveringOrphanedAccountsScreen(
    onAccountsDiscovered: (ImmutableList<RecoverableAccount>) -> Unit,
    onError: () -> Unit,
  ): ScreenModel {
    LaunchedEffect("discover orphaned accounts") {
      val recoverableAccountsResult = orphanedKeyRecoveryService
        .discoverRecoverableAccounts()

      recoverableAccountsResult.onSuccess { accounts ->
        when {
          accounts.isEmpty() -> {
            logWarn { "$LOG_TAG No recoverable accounts found despite having valid keys" }
            onError()
          }
          else -> onAccountsDiscovered(accounts.toImmutableList())
        }
      }.onFailure { error ->
        logWarn { "$LOG_TAG Failed to discover recoverable accounts: $error" }
        onError()
      }
    }

    return LoadingBodyModel(
      message = "Discovering recoverable accounts...",
      id = null
    ).asRootScreen()
  }

  @Composable
  private fun SelectingOrphanedAccountScreen(
    accounts: ImmutableList<RecoverableAccount>,
    initialSelectedAccount: RecoverableAccount?,
    onRecover: (RecoverableAccount) -> Unit,
    onBack: () -> Unit,
  ): ScreenModel {
    var selectedAccount by remember { mutableStateOf(initialSelectedAccount) }

    return OrphanedAccountSelectionBodyModel(
      accounts = accounts,
      selectedAccount = selectedAccount,
      onAccountSelected = { account -> selectedAccount = account },
      onRecover = {
        selectedAccount?.let { account -> onRecover(account) }
      },
      onBack = onBack,
      moneyDisplayFormatter = moneyDisplayFormatter,
      dateTimeFormatter = dateTimeFormatter,
      timeZoneProvider = timeZoneProvider
    ).asRootScreen()
  }

  @Composable
  private fun RecoveringFromOrphanedKeysScreenModel(
    recoverableAccount: RecoverableAccount,
    onSuccess: () -> Unit,
  ): ScreenModel {
    var uiState by remember {
      mutableStateOf<OrphanedKeyRecoveryUiState>(OrphanedKeyRecoveryUiState.Recovering)
    }

    LaunchedEffect("execute-recovery", uiState) {
      if (uiState == OrphanedKeyRecoveryUiState.Recovering) {
        orphanedKeyRecoveryService
          .recoverFromRecoverableAccount(recoverableAccount)
          .onSuccess { keybox ->
            keyboxDao.saveKeyboxAsActive(keybox)
              .onSuccess {
                uiState = OrphanedKeyRecoveryUiState.Success
              }
              .onFailure {
                logWarn { "$LOG_TAG Failed to save recovered keybox: $it" }
                uiState = OrphanedKeyRecoveryUiState.Error
              }
          }
          .onFailure {
            logWarn { "$LOG_TAG Failed to recover from orphaned keys: $it" }
            uiState = OrphanedKeyRecoveryUiState.Error
          }
      }
    }

    LaunchedEffect("recovery-success", uiState) {
      if (uiState == OrphanedKeyRecoveryUiState.Success) {
        onSuccess()
      }
    }

    return when (uiState) {
      OrphanedKeyRecoveryUiState.Recovering -> LoadingBodyModel(
        message = "Recovering your wallet...",
        id = null
      ).asRootScreen()

      OrphanedKeyRecoveryUiState.Success -> LoadingBodyModel(
        message = "Recovery successful!",
        id = null
      ).asRootScreen()

      else -> LoadingBodyModel(
        message = "Recovery failed",
        id = null
      ).asRootScreen()
    }
  }

  /**
   * Alert shown when an action taken is disabled due to the app being in Emergency Exit Kit mode.
   */
  private fun featureUnavailableForEekAlert(onDismiss: () -> Unit) =
    ButtonAlertModel(
      title = "Feature Unavailable",
      subline = "This feature is disabled in the Emergency Exit Kit app.",
      primaryButtonText = "OK",
      onPrimaryButtonClick = onDismiss,
      onDismiss = onDismiss
    )

  private sealed interface State {
    /**
     * Showing screen allowing customer to choose an option to access an account with,
     * either 'Set up a new wallet' or 'More options' which progress to
     */
    data object ShowingChooseAccountAccess : State

    /**
     * Showing screen allowing customer to choose what type of account/wallet they
     * want to create: hardware or software.
     */
    data object ShowingCreateAccountOptions : State

    /**
     * Showing screen allowing customer to choose from additional account access options,
     * 'Be a Recovery Contact' and 'Restore your wallet'.
     */
    data object ShowingAccountAccessMoreOptions : State

    /**
     * Showing screen explaining the process of becoming a Recovery Contact, before checking for
     * cloud backup and routing to the appropriate flow.
     */
    data object ShowingBeTrustedContactIntroduction : State

    /**
     * Showing debug menu which allows updating initial default [FullAccountConfig].
     */
    data object ShowingDebugMenu : State

    /**
     * Showing demo mode configuration screen which allows to use the app without physical hardware
     */
    data object ShowingDemoMode : State

    /**
     * Showing flow to create a new software wallet.
     */
    data object CreatingSoftwareWallet : State

    /**
     * Discovering recoverable accounts from orphaned keychain entries.
     */
    data object DiscoveringOrphanedAccounts : State

    /**
     * Showing UI to select which orphaned account to recover when multiple accounts are found.
     */
    data class SelectingOrphanedAccount(
      val accounts: ImmutableList<RecoverableAccount>,
      val selectedAccount: RecoverableAccount?,
    ) : State

    /**
     * Recovering from orphaned keys using the selected account.
     */
    data class RecoveringFromOrphanedKeys(
      val recoverableAccount: RecoverableAccount,
    ) : State
  }
}
