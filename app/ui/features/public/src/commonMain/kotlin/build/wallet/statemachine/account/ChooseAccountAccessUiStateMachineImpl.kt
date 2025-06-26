package build.wallet.statemachine.account

import androidx.compose.runtime.*
import bitkey.ui.framework.NavigatorPresenter
import bitkey.ui.screens.demo.DemoModeDisabledScreen
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitAssociation.EekBuild
import build.wallet.emergencyexitkit.EmergencyExitKitDataProvider
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.*
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachineImpl.State.*
import build.wallet.statemachine.account.create.CreateAccountOptionsModel
import build.wallet.statemachine.account.create.CreateSoftwareWalletProps
import build.wallet.statemachine.account.create.CreateSoftwareWalletUiStateMachine
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.dev.DebugMenuScreen
import build.wallet.ui.model.alert.ButtonAlertModel

@BitkeyInject(ActivityScope::class)
class ChooseAccountAccessUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val navigatorPresenter: NavigatorPresenter,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val emergencyExitKitDataProvider: EmergencyExitKitDataProvider,
  private val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
  private val createSoftwareWalletUiStateMachine: CreateSoftwareWalletUiStateMachine,
) : ChooseAccountAccessUiStateMachine {
  @Composable
  override fun model(props: ChooseAccountAccessUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(ShowingChooseAccountAccess) }

    val isEekBuild = remember { emergencyExitKitDataProvider.getAssociatedEekData() == EekBuild }
    val softwareWalletFlag by remember {
      softwareWalletIsEnabledFeatureFlag.flagValue()
    }.collectAsState()

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
            }
          ).asRootScreen()
        }
      }

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
  }
}
