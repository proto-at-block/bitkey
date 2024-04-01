package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.CloudEventTrackerScreenIdContext
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.compose.collections.immutableListOf
import build.wallet.keybox.AppDataDeleter
import build.wallet.keybox.CloudBackupDeleter
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.LoadingActiveFullAccountData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveLiteAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.CreatingFullAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.RecoveringAccountData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData
import build.wallet.statemachine.dev.analytics.AnalyticsOptionsUiProps
import build.wallet.statemachine.dev.analytics.AnalyticsOptionsUiStateMachine
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsOptionsUiProps
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsOptionsUiStateMachine
import build.wallet.statemachine.dev.lightning.LightningOptionsUiProps
import build.wallet.statemachine.dev.lightning.LightningOptionsUiStateMachine
import build.wallet.statemachine.recovery.cloud.CloudSignInUiProps
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachine
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.toImmutableList

class DebugMenuListStateMachineImpl(
  private val accountConfigUiStateMachine: AccountConfigUiStateMachine,
  private val analyticsOptionsUiStateMachine: AnalyticsOptionsUiStateMachine,
  private val appDataDeleter: AppDataDeleter,
  private val appStateDeleterOptionsUiStateMachine: AppStateDeleterOptionsUiStateMachine,
  private val appVariant: AppVariant,
  private val bitkeyDeviceOptionsUiStateMachine: BitkeyDeviceOptionsUiStateMachine,
  private val bitcoinNetworkPickerUiStateMachine: BitcoinNetworkPickerUiStateMachine,
  private val cloudBackupDeleter: CloudBackupDeleter,
  private val f8eEnvironmentPickerUiStateMachine: F8eEnvironmentPickerUiStateMachine,
  private val featureFlagsOptionsUiStateMachine: FeatureFlagsOptionsUiStateMachine,
  private val infoOptionsUiStateMachine: InfoOptionsUiStateMachine,
  private val lightningOptionsUiStateMachine: LightningOptionsUiStateMachine,
  private val onboardingAppKeyDeletionUiStateMachine: OnboardingAppKeyDeletionUiStateMachine,
  private val onboardingConfigStateMachine: OnboardingConfigStateMachine,
  private val cloudSignUiStateMachine: CloudSignInUiStateMachine,
) : DebugMenuListStateMachine {
  @Composable
  override fun model(props: DebugMenuListProps): BodyModel {
    var actionConfirmation: ActionConfirmationRequest? by remember { mutableStateOf(null) }
    var deleteAppDataRequest: DeleteAppDataRequest? by remember { mutableStateOf(null) }

    val templateFullAccountConfigData =
      when (val accountData = props.accountData) {
        is GettingStartedData -> accountData.templateFullAccountConfigData
        is HasActiveLiteAccountData -> accountData.accountUpgradeTemplateFullAccountConfigData
        else -> null
      }

    if (deleteAppDataRequest != null) {
      val interstitial = DeleteEffect(deleteAppDataRequest!!, props.accountData) {
        deleteAppDataRequest = null
        props.onClose()
      }
      if (interstitial != null) {
        return interstitial
      }
    }

    return DebugMenuBodyModel(
      title = "Debug Menu",
      onBack = props.onClose,
      groups =
        listOfNotNull(
          AccountConfigListGroupModel(props, templateFullAccountConfigData),
          OnboardingConfigListGroupModel(props),
          BitcoinNetworkPickerListGroupModel(templateFullAccountConfigData),
          F8eEnvironmentPickerListGroupModel(props),
          infoOptionsUiStateMachine.model(Unit),
          BitkeyDeviceOptionsListGroupModel(
            props = props,
            onActionConfirmationRequest = { actionConfirmation = it }
          ),
          LightningOptionsListGroupModel(props.onSetState),
          LogsListGroupModel(props.onSetState),
          AnalyticsOptionsListGroupModel(props.onSetState),
          FeatureFlagsOptionsListGroupModel(props.onSetState),
          NetworkingDebugOptionsListGroupModel(props.onSetState),
          KeyboxDeleterOptionsListGroupModel(
            onActionConfirmationRequest = { actionConfirmation = it },
            onDeleteKeybox = { deleteAppDataRequest = it }
          ),
          onboardingAppKeyDeletionUiStateMachine.model(
            props = OnboardingAppKeyDeletionProps(
              onConfirmationRequested = { accept ->
                actionConfirmation =
                  ActionConfirmationRequest(
                    gatedActionTitle = "Delete Onboarding App Key",
                    gatedAction = {
                      accept()
                      actionConfirmation = null
                    }
                  )
              }
            )
          )
        ).toImmutableList(),
      alertModel =
        actionConfirmation?.let {
          ActionConfirmationAlert(
            actionConfirmation = it,
            onDismiss = { actionConfirmation = null }
          )
        }
    )
  }

  @Composable
  private fun DeleteEffect(
    request: DeleteAppDataRequest,
    accountData: AccountData,
    onDone: () -> Unit,
  ): BodyModel? {
    val requiresLogin = !(accountData is AccountData.HasActiveFullAccountData || accountData is HasActiveLiteAccountData)
    var cloudLoggedIn: Boolean by remember { mutableStateOf(false) }

    if (requiresLogin && !cloudLoggedIn) {
      return cloudSignUiStateMachine.model(
        CloudSignInUiProps(
          forceSignOut = true,
          onSignedIn = { cloudLoggedIn = true },
          onSignInFailure = {
            log(LogLevel.Warn) { "Failed to sign in to cloud" }
            onDone()
          },
          eventTrackerContext = CloudEventTrackerScreenIdContext.DEBUG_MENU
        )
      )
    }
    LaunchedEffect("delete-app-data-$request)") {
      if (request.deleteAppKeyBackup) {
        cloudBackupDeleter.delete(cloudServiceProvider())
      }
      if (request.deleteAppKey) {
        appDataDeleter.deleteAll()
      }
      onDone()
    }
    return null
  }

  @Composable
  private fun ActionConfirmationAlert(
    actionConfirmation: ActionConfirmationRequest,
    onDismiss: () -> Unit,
  ): AlertModel {
    return AlertModel(
      title = actionConfirmation.gatedActionTitle,
      subline = "Are you sure?",
      onDismiss = onDismiss,
      primaryButtonText = "Yes",
      onPrimaryButtonClick = actionConfirmation.gatedAction,
      primaryButtonStyle = AlertModel.ButtonStyle.Destructive,
      secondaryButtonText = "Cancel",
      onSecondaryButtonClick = onDismiss
    )
  }

  @Composable
  private fun AccountConfigListGroupModel(
    props: DebugMenuListProps,
    templateFullAccountConfigData:
      TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData?,
  ): ListGroupModel? {
    return accountConfigUiStateMachine.model(
      AccountConfigProps(props.accountData, templateFullAccountConfigData)
    )
  }

  @Composable
  private fun OnboardingConfigListGroupModel(props: DebugMenuListProps): ListGroupModel? {
    return onboardingConfigStateMachine.model(
      OnboardingConfigProps(props.accountData)
    )
  }

  @Composable
  private fun BitcoinNetworkPickerListGroupModel(
    templateFullAccountConfigData:
      TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData?,
  ): ListGroupModel? {
    return bitcoinNetworkPickerUiStateMachine.model(
      BitcoinNetworkPickerUiProps(templateFullAccountConfigData)
    )
  }

  @Composable
  private fun BitkeyDeviceOptionsListGroupModel(
    props: DebugMenuListProps,
    onActionConfirmationRequest: (ActionConfirmationRequest) -> Unit,
  ): ListGroupModel? {
    val isHardwareFake = props.accountData.fullAccountConfig?.isHardwareFake ?: return null
    return bitkeyDeviceOptionsUiStateMachine.model(
      props =
        BitkeyDeviceOptionsUiProps(
          firmwareData = props.firmwareData ?: return null,
          onFirmwareUpdateClick = { updateFirmwareData ->
            props.onSetState(
              DebugMenuState.UpdatingFirmware(
                isHardwareFake = isHardwareFake,
                firmwareData = updateFirmwareData
              )
            )
          },
          onWipeBitkeyClick = {
            onActionConfirmationRequest(
              ActionConfirmationRequest(
                gatedActionTitle = "Wipe Hardware",
                gatedAction = { props.onSetState(DebugMenuState.WipingHardware(isHardwareFake)) }
              )
            )
          },
          onFirmwareMetadataClick = {
            props.onSetState(DebugMenuState.ShowingFirmwareMetadata(isHardwareFake))
          }
        )
    )
  }

  @Composable
  private fun LightningOptionsListGroupModel(setState: (DebugMenuState) -> Unit): ListGroupModel? {
    return lightningOptionsUiStateMachine.model(
      props =
        LightningOptionsUiProps(
          onLightningOptionsClick = { setState(DebugMenuState.ShowingLightningDebugMenu) }
        )
    )
  }

  @Composable
  private fun AnalyticsOptionsListGroupModel(setState: (DebugMenuState) -> Unit): ListGroupModel? {
    return analyticsOptionsUiStateMachine.model(
      props =
        AnalyticsOptionsUiProps(
          onShowAnalytics = { setState(DebugMenuState.ShowingAnalytics) }
        )
    )
  }

  @Composable
  private fun FeatureFlagsOptionsListGroupModel(
    setState: (DebugMenuState) -> Unit,
  ): ListGroupModel? {
    return featureFlagsOptionsUiStateMachine.model(
      props =
        FeatureFlagsOptionsUiProps(
          onShowFeatureFlags = { setState(DebugMenuState.ShowingFeatureFlags) }
        )
    )
  }

  @Composable
  private fun KeyboxDeleterOptionsListGroupModel(
    onActionConfirmationRequest: (ActionConfirmationRequest) -> Unit,
    onDeleteKeybox: (DeleteAppDataRequest) -> Unit,
  ): ListGroupModel? {
    return appStateDeleterOptionsUiStateMachine.model(
      props =
        AppStateDeleterOptionsUiProps(
          onDeleteAppKeyRequest = {
            onActionConfirmationRequest(
              ActionConfirmationRequest(
                gatedActionTitle = "Delete App Key",
                gatedAction = {
                  onDeleteKeybox(
                    DeleteAppDataRequest(
                      deleteAppKey = true,
                      deleteAppKeyBackup = false
                    )
                  )
                }
              )
            )
          },
          onDeleteAppKeyBackupRequest = {
            onActionConfirmationRequest(
              ActionConfirmationRequest(
                gatedActionTitle = "Delete App Key Backup",
                gatedAction = {
                  onDeleteKeybox(
                    DeleteAppDataRequest(
                      deleteAppKey = false,
                      deleteAppKeyBackup = true
                    )
                  )
                }
              )
            )
          },
          onDeleteAppKeyAndBackupRequest = {
            onActionConfirmationRequest(
              ActionConfirmationRequest(
                gatedActionTitle = "Delete App Key and Backup",
                gatedAction = {
                  onDeleteKeybox(
                    DeleteAppDataRequest(
                      deleteAppKey = true,
                      deleteAppKeyBackup = true
                    )
                  )
                }
              )
            )
          }
        )
    )
  }

  @Composable
  private fun NetworkingDebugOptionsListGroupModel(
    onSetState: (DebugMenuState) -> Unit,
  ): ListGroupModel? {
    return when (appVariant) {
      AppVariant.Customer -> null
      else ->
        ListGroupModel(
          header = "Debug Options",
          items =
            immutableListOf(
              ListItemModel(
                title = "Cloud Storage",
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = { onSetState(DebugMenuState.ShowingCloudStorageDebugOptions) }
              ),
              ListItemModel(
                title = "Networking",
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = { onSetState(DebugMenuState.ShowingNetworkingDebugOptions) }
              )
            ),
          style = ListGroupStyle.DIVIDER
        )
    }
  }

  @Composable
  private fun F8eEnvironmentPickerListGroupModel(props: DebugMenuListProps): ListGroupModel? {
    return f8eEnvironmentPickerUiStateMachine.model(
      F8eEnvironmentPickerUiProps(
        accountData = props.accountData,
        openCustomUrlInput = { customUrl, templateFullAccountConfigData ->
          props.onSetState(DebugMenuState.ShowingF8eCustomUrl(customUrl, templateFullAccountConfigData))
        }
      )
    )
  }

  @Composable
  private fun LogsListGroupModel(onSetState: (DebugMenuState) -> Unit): ListGroupModel? {
    // Don't show Logs in Customer build
    return when (appVariant) {
      AppVariant.Customer -> null
      else ->
        ListGroupModel(
          style = ListGroupStyle.DIVIDER,
          items =
            immutableListOf(
              ListItemModel(
                title = "Logs",
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = { onSetState(DebugMenuState.ShowingLogs) }
              )
            )
        )
    }
  }
}

/**
 * Represents a keybox unpairing request.
 *
 * @property deleteAppKey - determines if we should delete local app key.
 * @property deleteAppKeyBackup - determines if we should delete app key cloud backup.
 */
private data class DeleteAppDataRequest(
  val deleteAppKey: Boolean,
  val deleteAppKeyBackup: Boolean,
)

private data class ActionConfirmationRequest(
  val gatedActionTitle: String,
  val gatedAction: () -> Unit,
)

private val AccountData.fullAccountConfig: FullAccountConfig?
  get() =
    when (val accountData = this) {
      is ActiveFullAccountLoadedData -> accountData.account.keybox.config
      is LoadingActiveFullAccountData -> accountData.account.keybox.config
      is CreatingFullAccountData -> accountData.templateFullAccountConfig
      is GettingStartedData -> accountData.templateFullAccountConfigData.config
      is RecoveringAccountData -> accountData.templateFullAccountConfig
      is HasActiveLiteAccountData -> accountData.accountUpgradeTemplateFullAccountConfigData.config
      else -> null
    }
