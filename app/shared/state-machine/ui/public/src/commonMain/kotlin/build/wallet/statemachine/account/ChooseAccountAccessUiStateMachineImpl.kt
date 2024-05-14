package build.wallet.statemachine.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation
import build.wallet.emergencyaccesskit.EmergencyAccessKitDataProvider
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.demo.DemoModeConfigUiProps
import build.wallet.statemachine.demo.DemoModeConfigUiStateMachine
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.ui.model.alert.ButtonAlertModel

class ChooseAccountAccessUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val debugMenuStateMachine: DebugMenuStateMachine,
  private val demoModeConfigUiStateMachine: DemoModeConfigUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val emergencyAccessKitDataProvider: EmergencyAccessKitDataProvider,
) : ChooseAccountAccessUiStateMachine {
  @Composable
  override fun model(props: ChooseAccountAccessUiProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.ShowingChooseAccountAccess) }
    var alert by remember { mutableStateOf<ButtonAlertModel?>(null) }

    val eakAssociation = remember { emergencyAccessKitDataProvider.getAssociatedEakData() }

    val onBeTrustedContact: (() -> Unit)? =
      remember(eakAssociation) {
        when (eakAssociation) {
          EmergencyAccessKitAssociation.EakBuild -> null
          else -> ({ uiState = State.ShowingBeTrustedContactIntroduction })
        }
      }

    val onRestoreEmergencyAccessKit: (() -> Unit)? =
      remember(eakAssociation) {
        when (eakAssociation) {
          EmergencyAccessKitAssociation.EakBuild ->
            props.chooseAccountAccessData.startEmergencyAccessRecovery
          else -> null
        }
      }

    val onRestoreYourWallet: (() -> Unit)? =
      remember(eakAssociation) {
        when (eakAssociation) {
          EmergencyAccessKitAssociation.EakBuild -> null
          else -> props.chooseAccountAccessData.startRecovery
        }
      }

    return when (uiState) {
      is State.ShowingChooseAccountAccess ->
        ChooseAccountAccessScreen(
          onLogoClick = {
            // Only show the debug menu in non-customer builds
            when (appVariant) {
              AppVariant.Customer ->
                uiState = State.ShowingDemoMode

              AppVariant.Beta, AppVariant.Emergency ->
                Unit

              AppVariant.Team, AppVariant.Development ->
                uiState = State.ShowingDebugMenu
            }
          },
          onCreateWallet = props.chooseAccountAccessData.startFullAccountCreation,
          onMoreOptionsClick = { uiState = State.ShowingAccountAccessMoreOptions },
          eakAssociation = eakAssociation
        )

      is State.ShowingAccountAccessMoreOptions ->
        ScreenModel(
          body =
            AccountAccessMoreOptionsFormBodyModel(
              onBack = { uiState = State.ShowingChooseAccountAccess },
              onRestoreYourWalletClick = onRestoreYourWallet,
              onBeTrustedContactClick = onBeTrustedContact,
              onRestoreEmergencyAccessKit = onRestoreEmergencyAccessKit
            ),
          presentationStyle = Root,
          alertModel = alert
        )

      is State.ShowingBeTrustedContactIntroduction -> {
        BeTrustedContactIntroductionModel(
          onBack = { uiState = State.ShowingChooseAccountAccess },
          onContinue = props.chooseAccountAccessData.startLiteAccountCreation,
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        ).asRootScreen()
      }

      is State.ShowingDebugMenu ->
        DebugMenuScreen(
          props = props,
          onClose = { uiState = State.ShowingChooseAccountAccess }
        )

      is State.ShowingDemoMode ->
        DemoModeConfigScreen(
          props = props,
          onClose = { uiState = State.ShowingChooseAccountAccess }
        )
    }
  }

  @Composable
  private fun ChooseAccountAccessScreen(
    onLogoClick: () -> Unit,
    onCreateWallet: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    eakAssociation: EmergencyAccessKitAssociation,
  ): ScreenModel {
    var alert by remember { mutableStateOf<ButtonAlertModel?>(null) }
    val showDisabledAlert = {
      alert = DisabledForEakAlert(onDismiss = { alert = null })
    }

    return ScreenModel(
      body =
        ChooseAccountAccessModel(
          onLogoClick = onLogoClick,
          onSetUpNewWalletClick = onCreateWallet.disableForEak(eakAssociation, showDisabledAlert),
          onMoreOptionsClick = onMoreOptionsClick
        ),
      alertModel = alert,
      presentationStyle = ScreenPresentationStyle.RootFullScreen,
      colorMode = ScreenColorMode.Dark
    )
  }

  @Composable
  private fun DebugMenuScreen(
    props: ChooseAccountAccessUiProps,
    onClose: () -> Unit,
  ): ScreenModel =
    debugMenuStateMachine.model(
      props =
        DebugMenuProps(
          accountData = props.chooseAccountAccessData,
          firmwareData = props.firmwareData,
          onClose = onClose
        )
    )

  @Composable
  private fun DemoModeConfigScreen(
    props: ChooseAccountAccessUiProps,
    onClose: () -> Unit,
  ): ScreenModel =
    demoModeConfigUiStateMachine.model(
      props =
        DemoModeConfigUiProps(
          accountData = props.chooseAccountAccessData,
          onBack = onClose
        )
    )

  /**
   * Disables a callback if the current app is an emergency access kit build.
   */
  private fun (() -> Unit).disableForEak(
    eakAssociation: EmergencyAccessKitAssociation,
    alert: () -> Unit,
  ): () -> Unit {
    return when (eakAssociation) {
      EmergencyAccessKitAssociation.EakBuild -> alert
      else -> this
    }
  }

  /**
   * Alert shown when an action taken is disabled due to the app being in Emergency Access Kit mode.
   */
  private fun DisabledForEakAlert(onDismiss: () -> Unit) =
    ButtonAlertModel(
      title = "Feature Unavailable",
      subline = "This feature is disabled in the Emergency Access Kit app.",
      primaryButtonText = "OK",
      onPrimaryButtonClick = onDismiss,
      onDismiss = onDismiss
    )
}

private sealed interface State {
  /**
   * Showing screen allowing customer to choose an option to access an account with,
   * either 'Set up a new wallet' or 'More options' which progress to
   */
  data object ShowingChooseAccountAccess : State

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
}
