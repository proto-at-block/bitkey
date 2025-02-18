package build.wallet.statemachine.account

import androidx.compose.runtime.*
import bitkey.ui.framework.NavigatorPresenter
import bitkey.ui.screens.demo.DemoModeDisabledScreen
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation.EakBuild
import build.wallet.emergencyaccesskit.EmergencyAccessKitDataProvider
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.flags.SoftwareWalletIsEnabledFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.onboarding.CreateFullAccountContext
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.*
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachineImpl.State.*
import build.wallet.statemachine.account.create.CreateAccountOptionsModel
import build.wallet.statemachine.account.create.CreateSoftwareWalletProps
import build.wallet.statemachine.account.create.CreateSoftwareWalletUiStateMachine
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.ui.model.alert.ButtonAlertModel

@BitkeyInject(ActivityScope::class)
class ChooseAccountAccessUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val debugMenuStateMachine: DebugMenuStateMachine,
  private val navigatorPresenter: NavigatorPresenter,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val emergencyAccessKitDataProvider: EmergencyAccessKitDataProvider,
  private val softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
  private val createSoftwareWalletUiStateMachine: CreateSoftwareWalletUiStateMachine,
  private val createAccountUiStateMachine: CreateAccountUiStateMachine,
  private val inheritanceFeatureFlag: InheritanceFeatureFlag,
) : ChooseAccountAccessUiStateMachine {
  @Composable
  override fun model(props: ChooseAccountAccessUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(ShowingChooseAccountAccess) }

    val isEakBuild = remember { emergencyAccessKitDataProvider.getAssociatedEakData() == EakBuild }
    val softwareWalletFlag by remember {
      softwareWalletIsEnabledFeatureFlag.flagValue()
    }.collectAsState()

    return when (state) {
      is ShowingCreateAccountOptions -> {
        CreateAccountOptionsModel(
          onBack = { state = ShowingChooseAccountAccess },
          onUseHardwareClick = { state = CreatingFullAccount },
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
            if (isEakBuild) {
              alert = featureUnavailableForEakAlert(onDismiss = { alert = null })
            } else {
              if (softwareWalletFlag.value) {
                state = ShowingCreateAccountOptions
              } else {
                state = CreatingFullAccount
              }
            }
          },
          onMoreOptionsClick = { state = ShowingAccountAccessMoreOptions }
        ).asRootFullScreen(
          colorMode = ScreenColorMode.Dark,
          alertModel = alert
        )
      }

      is ShowingAccountAccessMoreOptions -> {
        if (isEakBuild) {
          EmergencyAccountAccessMoreOptionsFormBodyModel(
            onBack = { state = ShowingChooseAccountAccess },
            onRestoreEmergencyAccessKit = props.chooseAccountAccessData.startEmergencyAccessRecovery
          ).asRootScreen()
        } else {
          AccountAccessMoreOptionsFormBodyModel(
            onBack = { state = ShowingChooseAccountAccess },
            onRestoreYourWalletClick = props.chooseAccountAccessData.startRecovery,
            onBeTrustedContactClick = {
              if (inheritanceFeatureFlag.isEnabled()) {
                props.chooseAccountAccessData.startLiteAccountCreation()
              } else {
                state = ShowingBeTrustedContactIntroduction
              }
            },
            onResetExistingDevice = props.chooseAccountAccessData.wipeExistingDevice,
            isInheritanceEnabled = inheritanceFeatureFlag.isEnabled()
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

      is ShowingDebugMenu -> debugMenuStateMachine.model(
        props = DebugMenuProps(
          onClose = { state = ShowingChooseAccountAccess },
          onAppDataDeleted = {
            // no-op, where are already in a state with no data
          }
        )
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
      CreatingFullAccount -> createAccountUiStateMachine.model(
        props = CreateAccountUiProps(
          context = CreateFullAccountContext.NewFullAccount,
          rollback = {
            state = ShowingChooseAccountAccess
          },
          onOnboardingComplete = {
            // no-op
            // Data state machine will handle the transition
          }
        )
      )
    }
  }

  /**
   * Alert shown when an action taken is disabled due to the app being in Emergency Access Kit mode.
   */
  private fun featureUnavailableForEakAlert(onDismiss: () -> Unit) =
    ButtonAlertModel(
      title = "Feature Unavailable",
      subline = "This feature is disabled in the Emergency Access Kit app.",
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
     * 'Be a Trusted Contact' and 'Restore your wallet'.
     */
    data object ShowingAccountAccessMoreOptions : State

    /**
     * Showing screen explaining the process of becoming a trusted contact, before checking for
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
     * Showing flow to create a new full account.
     */
    data object CreatingFullAccount : State
  }
}
