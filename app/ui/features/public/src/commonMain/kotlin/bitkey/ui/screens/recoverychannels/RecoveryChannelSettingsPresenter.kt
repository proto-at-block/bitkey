package bitkey.ui.screens.recoverychannels

import androidx.compose.runtime.*
import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationPreferences
import bitkey.notifications.NotificationsPreferencesCachedProvider
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import bitkey.ui.screens.recoverychannels.RecoveryChannelSettingsScreenPresenter.RecoveryState.*
import bitkey.ui.screens.recoverychannels.RecoveryChannelSettingsScreenPresenter.RecoveryState.ShowingNotificationsSettingsUiState.OverlayState.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.notifications.NotificationTouchpointData
import build.wallet.notifications.NotificationTouchpointService
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.platform.permissions.Permission.PushNotifications
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PermissionStatus.*
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.platform.settings.TelephonyCountryCodeProvider
import build.wallet.platform.settings.isCountry
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.account.create.full.onboard.notifications.RECOVERY_INFO_URL
import build.wallet.statemachine.account.create.full.onboard.notifications.UiErrorHint
import build.wallet.statemachine.account.create.full.onboard.notifications.UiErrorHintKey
import build.wallet.statemachine.account.create.full.onboard.notifications.UiErrorHintsProvider
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.*
import build.wallet.statemachine.notifications.NotificationOperationApprovalInstructionsFormScreenModel
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.ActivationApprovalInstructionsUiState.ErrorBottomSheetState
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachine
import build.wallet.statemachine.platform.permissions.NotificationPermissionRequester
import build.wallet.statemachine.settings.full.notifications.*
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

data class RecoveryChannelSettingsScreen(
  val account: FullAccount,
  val source: Source = Source.Settings,
  override val origin: Screen?,
) : Screen

@BitkeyInject(ActivityScope::class)
class RecoveryChannelSettingsScreenPresenter(
  private val permissionChecker: PermissionChecker,
  private val notificationsPreferencesCachedProvider: NotificationsPreferencesCachedProvider,
  private val notificationTouchpointInputAndVerificationUiStateMachine:
    NotificationTouchpointInputAndVerificationUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val systemSettingsLauncher: SystemSettingsLauncher,
  private val eventTracker: EventTracker,
  private val notificationPermissionRequester: NotificationPermissionRequester,
  private val uiErrorHintsProvider: UiErrorHintsProvider,
  private val notificationTouchpointService: NotificationTouchpointService,
  private val usSmsFeatureFlag: UsSmsFeatureFlag,
) : ScreenPresenter<RecoveryChannelSettingsScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: RecoveryChannelSettingsScreen,
  ): ScreenModel {
    val smsErrorHint by remember { uiErrorHintsProvider.errorHintFlow(UiErrorHintKey.Phone) }
      .collectAsState()

    val notificationTouchpointData by remember {
      notificationTouchpointService.notificationTouchpointData()
    }.collectAsState(null)

    var state: RecoveryState by remember {
      mutableStateOf(
        ShowingNotificationsSettingsUiState(
          overlayState = LoadingPreferencesOverlayState
        )
      )
    }

    var notificationPreferences: NotificationPreferences by remember {
      mutableStateOf(
        NotificationPreferences(
          moneyMovement = emptySet(),
          productMarketing = emptySet(),
          accountSecurity = emptySet()
        )
      )
    }

    return when (val currentState = state) {
      is TogglingNotificationChannelUiState -> {
        val securitySet = notificationPreferences.accountSecurity.toMutableSet()

        if (securitySet.contains(currentState.notificationChannel)) {
          securitySet.remove(currentState.notificationChannel)
        } else {
          securitySet.add(currentState.notificationChannel)
        }
        val updatedPrefs = notificationPreferences.copy(accountSecurity = securitySet)

        UpdateNotificationsPreferences(
          screen = screen,
          updatedPrefs = updatedPrefs,
          state = currentState,
          hwFactorProofOfPossession = currentState.hwFactorProofOfPossession,
          setState = { state = it },
          updatedPrefsState = { notificationPreferences = it }
        ).asModalScreen()
      }

      is DisablingNotificationChannelProofOfHwPossessionUiState -> NotificationOperationApprovalInstructionsFormScreenModel(
        onExit = { state = ShowingNotificationsSettingsUiState() },
        operationDescription = currentState.notificationChannel.disableOperationDescription(
          notificationTouchpointData
        ),
        isApproveButtonLoading = false,
        errorBottomSheetState = ErrorBottomSheetState.Hidden,
        onApprove = {
          state = VerifyingProofOfHwPossessionUiState(currentState.notificationChannel)
        }
      )

      is VerifyingProofOfHwPossessionUiState -> VerifyingProofOfHwPossessionModel(
        screen = screen,
        onBack = { state = ShowingNotificationsSettingsUiState() },
        onSuccess = { proof ->
          state = TogglingNotificationChannelUiState(currentState.notificationChannel, proof)
        },
        operationDescriptiton = currentState.notificationChannel.disableOperationDescription(
          notificationTouchpointData
        )
      )

      is ShowingNotificationsSettingsUiState -> {
        LoadNotificationsPreferences(
          screen = screen,
          navigator = navigator,
          setState = { state = it },
          updatedPrefsState = { notificationPreferences = it }
        )

        ShowingMainScreen(
          state = currentState,
          screen = screen,
          navigator = navigator,
          notificationPreferences = notificationPreferences,
          phoneErrorHint = smsErrorHint,
          updateState = { state = it },
          notificationTouchpointData = notificationTouchpointData
        )
      }

      is EnteringAndVerifyingPhoneNumberUiState -> {
        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props = NotificationTouchpointInputAndVerificationProps(
            accountId = screen.account.accountId,
            touchpointType = NotificationTouchpointType.PhoneNumber,
            entryPoint = NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings,
            onSuccess = {
              state = if (notificationPreferences.accountSecurity.contains(NotificationChannel.Sms)) {
                ShowingNotificationsSettingsUiState()
              } else {
                TogglingNotificationChannelUiState(NotificationChannel.Sms, null)
              }
            },
            onClose = {
              state = ShowingNotificationsSettingsUiState()
            }
          )
        )
      }

      is EnteringAndVerifyingEmailUiState -> {
        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props = NotificationTouchpointInputAndVerificationProps(
            accountId = screen.account.accountId,
            touchpointType = NotificationTouchpointType.Email,
            entryPoint = NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings,
            onSuccess = {
              state = if (notificationPreferences.accountSecurity.contains(NotificationChannel.Email)) {
                ShowingNotificationsSettingsUiState()
              } else {
                TogglingNotificationChannelUiState(NotificationChannel.Email, null)
              }
            },
            onClose = {
              state = ShowingNotificationsSettingsUiState()
            }
          )
        )
      }

      ShowLearnRecoveryWebView -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = RECOVERY_INFO_URL,
              onClose = { state = ShowingNotificationsSettingsUiState() }
            )
          }
        ).asModalScreen()
      }
    }
  }

  @Composable
  private fun LoadNotificationsPreferences(
    screen: RecoveryChannelSettingsScreen,
    navigator: Navigator,
    setState: (RecoveryState) -> Unit,
    updatedPrefsState: (NotificationPreferences) -> Unit,
  ) {
    // Side effect: get prefs from server
    LaunchedEffect("load-notifications-preferences") {
      notificationsPreferencesCachedProvider.getNotificationsPreferences()
        .collect {
          it?.onSuccess { prefs ->
            updatedPrefsState(prefs)
            setState(ShowingNotificationsSettingsUiState())
          }?.onFailure { error ->
            setState(
              ShowingNotificationsSettingsUiState(
                overlayState = BottomSheetOverlayState(
                  bottomSheetModel = NetworkingErrorSheetModel(
                    onClose = {
                      if (screen.origin != null) {
                        navigator.goTo(screen.origin)
                      } else {
                        navigator.exit()
                      }
                    },
                    networkingError = error
                  )
                )
              )
            )
          }
        }
    }
  }

  @Composable
  private fun UpdateNotificationsPreferences(
    screen: RecoveryChannelSettingsScreen,
    updatedPrefs: NotificationPreferences,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
    state: RecoveryState,
    setState: (RecoveryState) -> Unit,
    updatedPrefsState: (NotificationPreferences) -> Unit,
  ): BodyModel {
    // Side effect: send updated preferences to server
    LaunchedEffect("update-notifications-preferences", state) {
      notificationsPreferencesCachedProvider.updateNotificationsPreferences(
        accountId = screen.account.accountId,
        preferences = updatedPrefs,
        hwFactorProofOfPossession = hwFactorProofOfPossession
      ).onSuccess {
        updatedPrefsState(updatedPrefs)
        setState(ShowingNotificationsSettingsUiState())
      }.onFailure { error ->
        setState(
          ShowingNotificationsSettingsUiState(
            overlayState = BottomSheetOverlayState(
              bottomSheetModel = NetworkingErrorSheetModel(
                onClose = { setState(ShowingNotificationsSettingsUiState()) },
                networkingError = error
              )
            )
          )
        )
      }
    }

    return LoadingSuccessBodyModel(
      id = NotificationsEventTrackerScreenId.RECOVERY_CHANNELS_SETTINGS_UPDATING_PREFERENCES,
      state = LoadingSuccessBodyModel.State.Loading
    )
  }

  @Composable
  private fun VerifyingProofOfHwPossessionModel(
    screen: RecoveryChannelSettingsScreen,
    operationDescriptiton: String,
    onBack: () -> Unit,
    onSuccess: (HwFactorProofOfPossession) -> Unit,
  ): ScreenModel {
    return proofOfPossessionNfcStateMachine.model(
      props = ProofOfPossessionNfcProps(
        request = Request.HwKeyProof(onSuccess),
        fullAccountId = screen.account.accountId,
        onBack = onBack,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        onTokenRefresh = {
          // Provide a screen model to show while the token is being refreshed.
          // We want this to be the same as [ActivationApprovalInstructionsUiState]
          // but with the button in a loading state
          NotificationOperationApprovalInstructionsFormScreenModel(
            onExit = onBack,
            operationDescription = operationDescriptiton,
            isApproveButtonLoading = true,
            errorBottomSheetState = ErrorBottomSheetState.Hidden,
            onApprove = {
              // No-op. Button is loading.
            }
          )
        },
        onTokenRefreshError = { isConnectivityError, _ ->
          // Provide a screen model to show if the token refresh results in an error.
          // We want this to be the same as [ActivationApprovalInstructionsUiState]
          // but with the error bottom sheet showing
          NotificationOperationApprovalInstructionsFormScreenModel(
            onExit = onBack,
            operationDescription = operationDescriptiton,
            isApproveButtonLoading = false,
            errorBottomSheetState =
              ErrorBottomSheetState.Showing(
                isConnectivityError = isConnectivityError,
                onClosed = onBack
              ),
            onApprove = {
              // No-op. Showing error sheet
            }
          )
        }
      )
    )
  }

  @Composable
  @Suppress("CyclomaticComplexMethod")
  private fun ShowingMainScreen(
    state: ShowingNotificationsSettingsUiState,
    screen: RecoveryChannelSettingsScreen,
    navigator: Navigator,
    notificationPreferences: NotificationPreferences,
    phoneErrorHint: UiErrorHint,
    updateState: (RecoveryState) -> Unit,
    notificationTouchpointData: NotificationTouchpointData?,
  ): ScreenModel {
    val usSmsEnabledFlag by remember {
      usSmsFeatureFlag.flagValue()
    }.collectAsState()

    val isDisabled = state.overlayState != None
    val isLoading = state.overlayState is LoadingPreferencesOverlayState
    val smsNumber = notificationTouchpointData?.phoneNumber?.formattedDisplayValue
    val emailAddress = notificationTouchpointData?.email?.value
    val isCountryUS = telephonyCountryCodeProvider.isCountry("us")
    val usSmsEnabled = usSmsEnabledFlag.value
    val smsRecoveryEnabled = smsNumber != null &&
      notificationPreferences.accountSecurity.contains(NotificationChannel.Sms)
    val pushRecoveryEnabled = permissionChecker.getPermissionStatus(
      PushNotifications
    ) == Authorized && notificationPreferences.accountSecurity.contains(NotificationChannel.Push)
    val smsRegisteredRecoveryDisabled = smsNumber != null &&
      !notificationPreferences.accountSecurity.contains(NotificationChannel.Sms)

    val missingRecoveryMethods = listOfNotNull(
      NotificationChannel.Sms.takeIf {
        /*
        1. We're not loading
        2. Either not a US sim card, or US SMS is enabled via feature flag, or the user entered a US number and got NotAvailableInYourCountry
        3. Sms is not in the list of enabled recovery options
         */
        !isLoading && (!isCountryUS || usSmsEnabled || smsNumber != null) &&
          !notificationPreferences.accountSecurity.contains(NotificationChannel.Sms)
      },
      NotificationChannel.Push.takeIf {
        !isLoading && !notificationPreferences.accountSecurity.contains(
          NotificationChannel.Push
        )
      }
    )

    if (state.overlayState is RequestingPushPermissionsOverlayState) {
      notificationPermissionRequester.requestNotificationPermission(
        onGranted = {
          eventTracker.track(Action.ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
          if (notificationPreferences.accountSecurity.contains(NotificationChannel.Push)) {
            updateState(ShowingNotificationsSettingsUiState())
          } else {
            updateState(
              TogglingNotificationChannelUiState(
                NotificationChannel.Push,
                null
              )
            )
          }
        },
        onDeclined = {
          eventTracker.track(Action.ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
          updateState(ShowingNotificationsSettingsUiState())
        }
      )
    }

    return RecoveryChannelsSettingsFormBodyModel(
      source = screen.source,
      missingRecoveryMethods = missingRecoveryMethods,
      pushItem = RecoveryChannelsSettingsFormItemModel(
        enabled = when {
          isLoading -> EnabledState.Loading
          isDisabled -> EnabledState.Disabled
          pushRecoveryEnabled -> EnabledState.Enabled
          else -> EnabledState.Disabled
        },
        uiErrorHint = null,
        onClick = {
          updateState(
            ShowingNotificationsSettingsUiState(
              BottomSheetOverlayState(
                bottomSheetModel = PushToggleOverlay(
                  source = screen.source,
                  pushRecoveryEnabled = pushRecoveryEnabled,
                  updateState = updateState
                )
              )
            )
          )
        }.takeIf { !isLoading }
      ),
      smsItem = RecoveryChannelsSettingsFormItemModel(
        displayValue = smsNumber,
        enabled = when {
          isLoading -> EnabledState.Loading
          isDisabled -> EnabledState.Disabled
          smsRecoveryEnabled -> EnabledState.Enabled
          else -> EnabledState.Disabled
        },
        uiErrorHint = phoneErrorHint.takeIf { it != UiErrorHint.None },
        onClick = {
          if (smsRecoveryEnabled) {
            updateState(
              ShowingNotificationsSettingsUiState(
                overlayState = BottomSheetOverlayState(
                  bottomSheetModel = SMSEditSheetModel(
                    source = screen.source,
                    onCancel = { updateState(ShowingNotificationsSettingsUiState()) },
                    onEnableDisable = {
                      updateState(
                        ShowingNotificationsSettingsUiState(
                          overlayState = AlertOverlayState(
                            alertModel = disableSmsRecoveryAlertModel(
                              onKeep = {
                                updateState(ShowingNotificationsSettingsUiState())
                              },
                              onDisable = {
                                updateState(
                                  DisablingNotificationChannelProofOfHwPossessionUiState(
                                    NotificationChannel.Sms
                                  )
                                )
                              }
                            )
                          )
                        )
                      )
                    },
                    onEditNumber = {
                      updateState(EnteringAndVerifyingPhoneNumberUiState)
                    },
                    enableNumber = null
                  )
                )
              )
            )
          } else if (smsRegisteredRecoveryDisabled) {
            updateState(
              ShowingNotificationsSettingsUiState(
                overlayState = BottomSheetOverlayState(
                  bottomSheetModel = SMSEditSheetModel(
                    source = screen.source,
                    onCancel = {
                      updateState(ShowingNotificationsSettingsUiState())
                    },
                    onEnableDisable = {
                      updateState(
                        TogglingNotificationChannelUiState(
                          notificationChannel = NotificationChannel.Sms,
                          hwFactorProofOfPossession = null
                        )
                      )
                    },
                    onEditNumber = {
                      updateState(EnteringAndVerifyingPhoneNumberUiState)
                    },
                    enableNumber = smsNumber
                  )
                )
              )
            )
          } else {
            if (isCountryUS && !usSmsEnabled) {
              updateState(
                ShowingNotificationsSettingsUiState(
                  overlayState = BottomSheetOverlayState(
                    bottomSheetModel = SMSNonUSSheetModel(
                      source = screen.source,
                      onCancel = {
                        updateState(ShowingNotificationsSettingsUiState())
                      },
                      onContinue = {
                        updateState(EnteringAndVerifyingPhoneNumberUiState)
                      }
                    )
                  )
                )
              )
            } else {
              updateState(EnteringAndVerifyingPhoneNumberUiState)
            }
          }
        }.takeIf { !isLoading }
      ),
      emailItem = RecoveryChannelsSettingsFormItemModel(
        displayValue = emailAddress,
        enabled = when {
          isLoading -> EnabledState.Loading
          isDisabled -> EnabledState.Disabled
          notificationPreferences.accountSecurity.contains(NotificationChannel.Email) -> EnabledState.Enabled
          else -> EnabledState.Disabled
        },
        uiErrorHint = null,
        onClick = { updateState(EnteringAndVerifyingEmailUiState) }.takeIf { !isLoading }
      ),
      alertModel = (state.overlayState as? AlertOverlayState)?.alertModel,
      continueOnClick = null,
      onBack = if (screen.origin != null) {
        {
          navigator.goTo(screen.origin)
        }
      } else {
        { navigator.exit() }
      },
      learnOnClick = {
        updateState(ShowLearnRecoveryWebView)
      },
      bottomSheetModel = (state.overlayState as? BottomSheetOverlayState)?.bottomSheetModel
    )
  }

  private fun PushToggleOverlay(
    source: Source,
    pushRecoveryEnabled: Boolean,
    updateState: (RecoveryState) -> Unit,
  ): SheetModel {
    return PushToggleSheetModel(
      source = source,
      onCancel = { updateState(ShowingNotificationsSettingsUiState()) },
      isEnabled = pushRecoveryEnabled,
      onToggle = {
        if (pushRecoveryEnabled) {
          updateState(
            ShowingNotificationsSettingsUiState(
              overlayState = AlertOverlayState(
                alertModel = disableRecoveryPushNotificationsAlertModel(
                  onKeep = {
                    updateState(ShowingNotificationsSettingsUiState())
                  },
                  onDisable = {
                    updateState(
                      DisablingNotificationChannelProofOfHwPossessionUiState(
                        NotificationChannel.Push
                      )
                    )
                  }
                )
              )
            )
          )
        } else {
          when (permissionChecker.getPermissionStatus(PushNotifications)) {
            NotDetermined -> {
              updateState(
                ShowingNotificationsSettingsUiState(
                  overlayState = RequestingPushPermissionsOverlayState
                )
              )
            }
            Denied -> {
              updateState(
                ShowingNotificationsSettingsUiState(
                  overlayState = AlertOverlayState(
                    alertModel = ButtonAlertModel(
                      title = "Open Settings to enable push notifications",
                      subline = "",
                      primaryButtonText = "Settings",
                      secondaryButtonText = "Close",
                      onDismiss = {
                        updateState(ShowingNotificationsSettingsUiState())
                      },
                      onPrimaryButtonClick = {
                        updateState(ShowingNotificationsSettingsUiState())
                        systemSettingsLauncher.launchAppSettings()
                      },
                      onSecondaryButtonClick = {
                        updateState(ShowingNotificationsSettingsUiState())
                      }
                    )
                  )
                )
              )
            }
            Authorized -> {
              updateState(
                TogglingNotificationChannelUiState(
                  notificationChannel = NotificationChannel.Push,
                  hwFactorProofOfPossession = null
                )
              )
            }
          }
        }
      }
    )
  }

  private fun NotificationChannel.disableOperationDescription(
    notificationTouchpointData: NotificationTouchpointData?,
  ): String {
    return when (this) {
      NotificationChannel.Email -> notificationTouchpointData?.email?.value ?: "(Email Address)"
      NotificationChannel.Push -> "Push Notification"
      NotificationChannel.Sms ->
        notificationTouchpointData?.phoneNumber?.formattedDisplayValue
          ?: "(SMS Number)"
    }
  }

  private sealed interface RecoveryState {
    /**
     * In editing state. If there are alerts or errors, other input should be disabled.
     */
    data class ShowingNotificationsSettingsUiState(
      val overlayState: OverlayState = None,
    ) : RecoveryState {
      sealed interface OverlayState {
        /** No overlaid info */
        data object None : OverlayState

        /**
         * Loading should be extremely short because data should be cached, but it's not zero, and
         * it's *possible* data won't be in the cache. The UI needs to display the data in a way that
         * doesn't cause "incorrect flickering". For example, showing "Disabled" for a recovery channel
         * that is in fact enabled.
         */
        data object LoadingPreferencesOverlayState : OverlayState

        data class BottomSheetOverlayState(val bottomSheetModel: SheetModel) : OverlayState

        data class AlertOverlayState(val alertModel: ButtonAlertModel) : OverlayState

        data object RequestingPushPermissionsOverlayState : OverlayState
      }
    }

    /**
     * Entering phone number.
     */
    data object EnteringAndVerifyingPhoneNumberUiState : RecoveryState

    /**
     * Entering email.
     */
    data object EnteringAndVerifyingEmailUiState : RecoveryState

    data class TogglingNotificationChannelUiState(
      val notificationChannel: NotificationChannel,
      val hwFactorProofOfPossession: HwFactorProofOfPossession?,
    ) : RecoveryState

    data class DisablingNotificationChannelProofOfHwPossessionUiState(
      val notificationChannel: NotificationChannel,
    ) : RecoveryState

    data class VerifyingProofOfHwPossessionUiState(
      val notificationChannel: NotificationChannel,
    ) : RecoveryState

    data object ShowLearnRecoveryWebView : RecoveryState
  }
}
