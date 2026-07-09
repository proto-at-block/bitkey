package build.wallet.statemachine.dev

import androidx.compose.runtime.*
import build.wallet.account.AccountService
import build.wallet.bitkey.account.Account
import build.wallet.coachmark.CoachmarkService
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.notifications.TestNotificationF8eClient
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.platform.config.AppVariant
import build.wallet.platform.system.exitProcess
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.dev.analytics.AnalyticsOptionsUiProps
import build.wallet.statemachine.dev.analytics.AnalyticsOptionsUiStateMachine
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsOptionsUiProps
import build.wallet.statemachine.dev.featureFlags.FeatureFlagsOptionsUiStateMachine
import build.wallet.statemachine.dev.wallet.BitcoinWalletDebugScreen
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class DebugMenuListStateMachineImpl(
  private val accountService: AccountService,
  private val accountConfigUiStateMachine: AccountConfigUiStateMachine,
  private val analyticsOptionsUiStateMachine: AnalyticsOptionsUiStateMachine,
  private val appVariant: AppVariant,
  private val bitkeyDeviceOptionsUiStateMachine: BitkeyDeviceOptionsUiStateMachine,
  private val bitcoinNetworkPickerUiStateMachine: BitcoinNetworkPickerUiStateMachine,
  private val f8eEnvironmentPickerUiStateMachine: F8eEnvironmentPickerUiStateMachine,
  private val featureFlagsOptionsUiStateMachine: FeatureFlagsOptionsUiStateMachine,
  private val infoOptionsUiStateMachine: InfoOptionsUiStateMachine,
  private val onboardingConfigStateMachine: OnboardingConfigStateMachine,
  private val coachmarkService: CoachmarkService,
  private val testNotificationF8eClient: TestNotificationF8eClient,
  private val bdk2FeatureFlag: Bdk2FeatureFlag,
  private val exchangeRateService: ExchangeRateService,
) : DebugMenuListStateMachine {
  @Composable
  override fun model(props: DebugMenuListProps): BodyModel {
    val account = remember { accountService.activeAccount() }.collectAsState(null).value
    var actionConfirmation: ActionConfirmationRequest? by remember { mutableStateOf(null) }
    var resetCoachmarks by remember { mutableStateOf(false) }

    // Search filter state
    var filterText by remember { mutableStateOf("") }

    if (resetCoachmarks) {
      LaunchedEffect("reset-coachmarks") {
        coachmarkService.resetCoachmarks()
      }
    }

    // Build all groups - ordered by user preference
    val allGroups = immutableListOfNotNull(
      // 1. Feature Flags
      FeatureFlagsOptionsListGroupModel(props.onSetState),
      // 2. Onboarding
      onboardingConfigStateMachine.model(Unit),
      // 3. Debug Options
      DebugOptionsListGroupModel(
        account,
        onActionConfirmationRequest = { actionConfirmation = it },
        props.onSetState,
        resetCoachmarks = {
          resetCoachmarks = true
          actionConfirmation = null
        }
      ),
      // 4. Logs
      LogsListGroupModel(props.onSetState),
      // 5. F8e Environment
      f8eEnvironmentPickerUiStateMachine.model(
        F8eEnvironmentPickerUiProps(
          openCustomUrlInput = { customUrl ->
            props.onSetState(DebugMenuState.ShowingF8eCustomUrl(customUrl))
          }
        )
      ),
      // 6. Bitcoin network
      bitcoinNetworkPickerUiStateMachine.model(Unit),
      // 7. Hardware
      BitkeyDeviceOptionsListGroupModel(
        props = props,
        onActionConfirmationRequest = { actionConfirmation = it }
      ),
      // 8. Identifiers
      infoOptionsUiStateMachine.model(
        InfoOptionsProps(
          onPasteboardCopy = props.onPasteboardCopy
        )
      ),
      // 9. Analytics
      AnalyticsOptionsListGroupModel(props.onSetState),
      // 10. Data Management
      DataManagementListGroupModel(props.onSetState),
      // 11. Keybox Configuration (at the end)
      accountConfigUiStateMachine.model(
        AccountConfigProps(
          onBitcoinWalletClick = {
            props.navigator.goTo(BitcoinWalletDebugScreen)
          }
        )
      )
    )

    // Apply filtering if search text is present
    val filteredGroups = if (filterText.isBlank()) {
      allGroups
    } else {
      allGroups.mapNotNull { group ->
        filterGroup(group, filterText)
      }.toImmutableList()
    }

    return DebugMenuBodyModel(
      title = "Debug Menu",
      onBack = props.onClose,
      groups = filteredGroups,
      filterText = filterText,
      onFilterChange = { filterText = it },
      collapsedGroupHeaders = props.collapsedGroupHeaders,
      onToggleGroupCollapse = props.onToggleGroupCollapse,
      alertModel =
        actionConfirmation?.let {
          ActionConfirmationAlert(
            actionConfirmation = it,
            onDismiss = { actionConfirmation = null }
          )
        }
    )
  }

  /**
   * Filters a ListGroupModel by the search query.
   * Returns null if no items match, otherwise returns the group with only matching items.
   */
  private fun filterGroup(
    group: ListGroupModel,
    query: String,
  ): ListGroupModel? {
    val lowerQuery = query.lowercase()
    val matchingItems = group.items.filter { item ->
      item.title.lowercase().contains(lowerQuery) ||
        item.secondaryText?.lowercase()?.contains(lowerQuery) == true
    }

    return if (matchingItems.isEmpty()) {
      null
    } else {
      group.copy(items = matchingItems.toImmutableList())
    }
  }

  @Composable
  private fun ActionConfirmationAlert(
    actionConfirmation: ActionConfirmationRequest,
    onDismiss: () -> Unit,
  ): ButtonAlertModel {
    return ButtonAlertModel(
      title = actionConfirmation.gatedActionTitle,
      subline = "Are you sure?",
      onDismiss = onDismiss,
      primaryButtonText = "Yes",
      onPrimaryButtonClick = {
        actionConfirmation.gatedAction()
        onDismiss()
      },
      primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
      secondaryButtonText = "Cancel",
      onSecondaryButtonClick = onDismiss
    )
  }

  @Composable
  private fun BitkeyDeviceOptionsListGroupModel(
    props: DebugMenuListProps,
    onActionConfirmationRequest: (ActionConfirmationRequest) -> Unit,
  ): ListGroupModel? {
    return bitkeyDeviceOptionsUiStateMachine.model(
      props =
        BitkeyDeviceOptionsUiProps(
          firmwareData = props.firmwareData ?: return null,
          onFirmwareUpdateClick = {
            props.onSetState(DebugMenuState.VerifyingFirmwareMetadata)
          },
          onWipeBitkeyClick = {
            onActionConfirmationRequest(
              ActionConfirmationRequest(
                gatedActionTitle = "Wipe Hardware",
                gatedAction = { props.onSetState(DebugMenuState.WipingHardware) }
              )
            )
          },
          onFirmwareMetadataClick = {
            props.onSetState(DebugMenuState.ShowingFirmwareMetadata)
          }
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

  private fun DataManagementListGroupModel(setState: (DebugMenuState) -> Unit): ListGroupModel =
    ListGroupModel(
      header = "Data Management",
      style = ListGroupStyle.DIVIDER,
      items = immutableListOf(
        ListItemModel(
          title = "Manual Key Deletion",
          secondaryText = "Choose exact local and cloud key material to delete.",
          trailingAccessory = ListItemAccessory.drillIcon(),
          onClick = { setState(DebugMenuState.ShowingManualKeyDeletion) }
        ),
        ListItemModel(
          title = "Recovery Scenario Presets",
          secondaryText = "Prepare full-reset recovery states like lost app or lost cloud.",
          trailingAccessory = ListItemAccessory.drillIcon(),
          onClick = { setState(DebugMenuState.ShowingRecoveryScenarioPresets) }
        ),
        ListItemModel(
          title = "Cloud Storage Browser",
          secondaryText = "View and delete individual cloud backup entries.",
          trailingAccessory = ListItemAccessory.drillIcon(),
          onClick = { setState(DebugMenuState.ShowingCloudStorageDebugOptions) }
        )
      )
    )

  @Composable
  private fun DebugOptionsListGroupModel(
    account: Account?,
    onActionConfirmationRequest: (ActionConfirmationRequest) -> Unit,
    onSetState: (DebugMenuState) -> Unit,
    resetCoachmarks: () -> Unit,
  ): ListGroupModel? {
    val scope = rememberStableCoroutineScope()
    val bdk2FlagValue by remember { bdk2FeatureFlag.flagValue() }.collectAsState()
    val isBdk2Enabled = bdk2FlagValue.value
    val bdk2SwitchTitle = if (isBdk2Enabled) "Switch to Legacy BDK" else "Switch to BDK 2"

    return when (appVariant) {
      AppVariant.Customer -> null
      else ->
        ListGroupModel(
          header = "Debug Options",
          items =
            immutableListOfNotNull(
              ListItemModel(
                title = "Networking",
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = { onSetState(DebugMenuState.ShowingNetworkingDebugOptions) }
              ),
              if (appVariant != AppVariant.Customer) {
                ListItemModel(
                  title = "Mock Tx & Exchange Rate Data",
                  trailingAccessory = ListItemAccessory.drillIcon(),
                  onClick = { onSetState(DebugMenuState.ShowingMockDataProvider) }
                )
              } else {
                null
              },
              ListItemModel(
                title = bdk2SwitchTitle,
                secondaryText = "Sets flag and closes the app to apply.",
                onClick = {
                  scope.launch {
                    bdk2FeatureFlag.setFlagValue(BooleanFlag(!isBdk2Enabled), overridden = true)
                    exitProcess(status = 0)
                  }
                }
              ),
              ListItemModel(
                title = "Reset Coachmarks",
                onClick = {
                  onActionConfirmationRequest(
                    ActionConfirmationRequest(
                      gatedActionTitle = "Reset all coachmarks?",
                      gatedAction = { resetCoachmarks() }
                    )
                  )
                }
              ),
              ListItemModel(
                title = "Clear Exchange Rates",
                secondaryText = "Forces rate refresh on next sell flow",
                onClick = {
                  scope.launch {
                    exchangeRateService.clearRates()
                  }
                }
              ),
              ListItemModel(
                title = "Test Notification",
                onClick = {
                  account?.let { acc ->
                    scope.launch {
                      testNotificationF8eClient.notification(
                        acc.accountId,
                        acc.config.f8eEnvironment
                      )
                    }
                  }
                }
              ),
              ListItemModel(
                title = "Reset Onboarding Timestamp",
                onClick = {
                  onActionConfirmationRequest(
                    ActionConfirmationRequest(
                      gatedActionTitle = "Clear onboarding timestamp?",
                      gatedAction = { onSetState(DebugMenuState.ClearingOnboardingData.OnboardingTimestamp) }
                    )
                  )
                }
              ),
              ListItemModel(
                title = "Reset Has Seen Upsell",
                onClick = {
                  onActionConfirmationRequest(
                    ActionConfirmationRequest(
                      gatedActionTitle = "Clear has seen upsell flag?",
                      gatedAction = { onSetState(DebugMenuState.ClearingOnboardingData.HasSeenUpsell) }
                    )
                  )
                }
              ),
              ListItemModel(
                title = "Fake Hardware Seed",
                secondaryText = "View/share mock hardware seed",
                trailingAccessory = ListItemAccessory.drillIcon(),
                onClick = { onSetState(DebugMenuState.ShowingFakeHardwareSeed) }
              )
            ),
          style = ListGroupStyle.DIVIDER
        )
    }
  }

  @Composable
  private fun LogsListGroupModel(onSetState: (DebugMenuState) -> Unit): ListGroupModel? {
    // Don't show Logs in Customer build
    return when (appVariant) {
      AppVariant.Customer -> null
      else ->
        ListGroupModel(
          header = "Logs",
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

private data class ActionConfirmationRequest(
  val gatedActionTitle: String,
  val gatedAction: () -> Unit,
)
