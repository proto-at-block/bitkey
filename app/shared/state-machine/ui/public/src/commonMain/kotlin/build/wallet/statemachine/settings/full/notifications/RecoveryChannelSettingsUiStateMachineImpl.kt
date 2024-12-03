package build.wallet.statemachine.settings.full.notifications

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.account.FullAccount
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.notifications.*
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
import build.wallet.statemachine.notifications.*
import build.wallet.statemachine.platform.permissions.NotificationPermissionRequester
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachineImpl.RecoveryState.*
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachineImpl.RecoveryState.ShowingNotificationsSettingsUiState.*
import build.wallet.statemachine.settings.full.notifications.Source.Settings
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RecoveryChannelSettingsUiStateMachineImpl(
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
) : RecoveryChannelSettingsUiStateMachine {
  @Composable
  override fun model(props: RecoveryChannelSettingsProps): ScreenModel {
    val scope = rememberStableCoroutineScope()
    val smsErrorHint = uiErrorHintsProvider.errorHintFlow(UiErrorHintKey.Phone).collectAsState()
    val notificationTouchpointData = remember {
      notificationTouchpointService.notificationTouchpointData()
    }.collectAsState(null).value

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

    LoadNotificationsPreferences(
      props = props,
      setState = { state = it },
      updatedPrefsState = { notificationPreferences = it }
    )

    return when (val stateVal = state) {
      is TogglingNotificationChannelUiState -> {
        val securitySet = notificationPreferences.accountSecurity.toMutableSet()

        if (securitySet.contains(stateVal.notificationChannel)) {
          securitySet.remove(stateVal.notificationChannel)
        } else {
          securitySet.add(stateVal.notificationChannel)
        }
        val updatedPrefs = notificationPreferences.copy(accountSecurity = securitySet)

        return UpdateNotificationsPreferences(
          props = props,
          updatedPrefs = updatedPrefs,
          state = stateVal,
          hwFactorProofOfPossession = stateVal.hwFactorProofOfPossession,
          setState = { state = it },
          updatedPrefsState = { notificationPreferences = it }
        ).asModalScreen()
      }

      is DisablingNotificationChannelProofOfHwPossessionUiState ->
        return NotificationOperationApprovalInstructionsFormScreenModel(
          onExit = { state = ShowingNotificationsSettingsUiState() },
          operationDescription = stateVal.notificationChannel.disableOperationDescription(
            notificationTouchpointData
          ),
          isApproveButtonLoading = false,
          errorBottomSheetState = NotificationTouchpointInputAndVerificationUiState.ActivationApprovalInstructionsUiState.ErrorBottomSheetState.Hidden,
          onApprove = {
            state = VerifyingProofOfHwPossessionUiState(stateVal.notificationChannel)
          }
        )

      is VerifyingProofOfHwPossessionUiState -> {
        return VerifyingProofOfHwPossessionModel(
          props = props,
          onBack = { state = ShowingNotificationsSettingsUiState() },
          onSuccess = { proof ->
            state =
              TogglingNotificationChannelUiState(stateVal.notificationChannel, proof)
          },
          operationDescriptiton = stateVal.notificationChannel.disableOperationDescription(
            notificationTouchpointData
          )
        )
      }

      is ShowingNotificationsSettingsUiState ->
        ShowingMainScreen(
          stateVal = stateVal,
          props = props,
          notificationPreferences = notificationPreferences,
          scope = scope,
          smsErrorHint.value,
          updateState = { state = it },
          notificationTouchpointData = notificationTouchpointData
        )

      is EnteringAndVerifyingPhoneNumberUiState -> {
        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props =
            NotificationTouchpointInputAndVerificationProps(
              accountId = props.account.accountId,
              accountConfig = (props.account as FullAccount).keybox.config,
              touchpointType = NotificationTouchpointType.PhoneNumber,
              entryPoint = NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings,
              onSuccess = {
                state =
                  if (notificationPreferences.accountSecurity.contains(NotificationChannel.Sms)) {
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
          props =
            NotificationTouchpointInputAndVerificationProps(
              accountId = props.account.accountId,
              accountConfig = (props.account as FullAccount).keybox.config,
              touchpointType = NotificationTouchpointType.Email,
              entryPoint = NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings,
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
    props: RecoveryChannelSettingsProps,
    setState: (RecoveryState) -> Unit,
    updatedPrefsState: (NotificationPreferences) -> Unit,
  ) {
    // Side effect: get prefs from server
    LaunchedEffect("load-notifications-preferences") {
      notificationsPreferencesCachedProvider.getNotificationsPreferences(
        props.account.config.f8eEnvironment,
        props.account.accountId
      ).collect {
        it.onSuccess { prefs ->
          updatedPrefsState(prefs)
          setState(ShowingNotificationsSettingsUiState())
        }.onFailure { error ->
          setState(
            ShowingNotificationsSettingsUiState(
              overlayState = BottomSheetOverlayState(
                bottomSheetModel = NetworkingErrorSheetModel(
                  onClose = { props.onBack() },
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
    props: RecoveryChannelSettingsProps,
    updatedPrefs: NotificationPreferences,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
    state: RecoveryState,
    setState: (RecoveryState) -> Unit,
    updatedPrefsState: (NotificationPreferences) -> Unit,
  ): BodyModel {
    // Side effect: send updated preferences to server
    LaunchedEffect("update-notifications-preferences", state) {
      notificationsPreferencesCachedProvider.updateNotificationsPreferences(
        f8eEnvironment = props.account.config.f8eEnvironment,
        accountId = props.account.accountId,
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

    // Return model
    return LoadingSuccessBodyModel(
      id = NotificationsEventTrackerScreenId.RECOVERY_CHANNELS_SETTINGS_UPDATING_PREFERENCES,
      state = LoadingSuccessBodyModel.State.Loading
    )
  }

  @Composable
  private fun VerifyingProofOfHwPossessionModel(
    props: RecoveryChannelSettingsProps,
    operationDescriptiton: String,
    onBack: () -> Unit,
    onSuccess: (HwFactorProofOfPossession) -> Unit,
  ): ScreenModel {
    return proofOfPossessionNfcStateMachine.model(
      props =
        ProofOfPossessionNfcProps(
          request = Request.HwKeyProof(onSuccess),
          fullAccountConfig = (props.account as FullAccount).keybox.config,
          fullAccountId = props.account.accountId,
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
              errorBottomSheetState = NotificationTouchpointInputAndVerificationUiState.ActivationApprovalInstructionsUiState.ErrorBottomSheetState.Hidden,
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
                NotificationTouchpointInputAndVerificationUiState.ActivationApprovalInstructionsUiState.ErrorBottomSheetState.Showing(
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
    stateVal: ShowingNotificationsSettingsUiState,
    props: RecoveryChannelSettingsProps,
    notificationPreferences: NotificationPreferences,
    scope: CoroutineScope,
    phoneErrorHint: UiErrorHint,
    updateState: (RecoveryState) -> Unit,
    notificationTouchpointData: NotificationTouchpointData?,
  ): ScreenModel {
    val delayedAlertOverlay = (stateVal.overlayState as? AlertOverlayState)?.alertModel

    // iOS won't show alerts while sheet animations are running...
    if (delayedAlertOverlay != null) {
      scope.launch {
        delay(800)
        updateState(
          ShowingNotificationsSettingsUiState(
            overlayState =
              DelayedAlertOverlayState(alertModel = delayedAlertOverlay)
          )
        )
      }
    }

    val isDisabled = delayedAlertOverlay != null
    val isLoading =
      stateVal.overlayState is LoadingPreferencesOverlayState
    val smsNumber = notificationTouchpointData?.phoneNumber?.formattedDisplayValue
    val emailAddress = notificationTouchpointData?.email?.value
    val isCountryUS = telephonyCountryCodeProvider.isCountry("us")
    val smsRecoveryEnabled =
      smsNumber != null && notificationPreferences.accountSecurity.contains(NotificationChannel.Sms)
    val pushRecoveryEnabled =
      permissionChecker.getPermissionStatus(PushNotifications) == Authorized &&
        notificationPreferences.accountSecurity.contains(NotificationChannel.Push)
    val smsRegisteredRecoveryDisabled =
      smsNumber != null && !notificationPreferences.accountSecurity.contains(NotificationChannel.Sms)

    // Either the phone sim says it's a US number, or the user entered a US number and got an error
    val usSimOrWarned = isCountryUS || phoneErrorHint != UiErrorHint.None

    val missingRecoveryMethods = listOfNotNull(
      NotificationChannel.Sms.takeIf {
        /*
        1. We're not loading
        2. Either not a US sim card, or the user entered a US number and got NotAvailableInYourCountry
        3. Sms is not in the list of enabled recovery options
         */
        !isLoading &&
          (!usSimOrWarned || smsNumber != null) &&
          !notificationPreferences.accountSecurity.contains(NotificationChannel.Sms)
      },
      NotificationChannel.Push.takeIf {
        !isLoading && !notificationPreferences.accountSecurity.contains(
          NotificationChannel.Push
        )
      }
    )

    if (stateVal.overlayState is RequestingPushPermissionsOverlayState) {
      notificationPermissionRequester.requestNotificationPermission(
        onGranted = {
          eventTracker.track(Action.ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
          updateState(
            TogglingNotificationChannelUiState(
              NotificationChannel.Push,
              null
            )
          )
        },
        onDeclined = {
          eventTracker.track(Action.ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
          updateState(ShowingNotificationsSettingsUiState())
        }
      )
    }

    return RecoveryChannelsSettingsFormBodyModel(
      source = Settings,
      missingRecoveryMethods = missingRecoveryMethods,
      pushItem = RecoveryChannelsSettingsFormItemModel(
        enabled = when {
          isLoading -> EnabledState.Loading
          isDisabled -> EnabledState.Disabled
          permissionChecker.getPermissionStatus(PushNotifications) == Authorized && notificationPreferences.accountSecurity.contains(
            NotificationChannel.Push
          ) -> EnabledState.Enabled
          else -> EnabledState.Disabled
        },
        uiErrorHint = null,
        onClick = {
          updateState(
            ShowingNotificationsSettingsUiState(
              BottomSheetOverlayState(
                bottomSheetModel = PushToggleOverlay(
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
                    onCancel = { updateState(ShowingNotificationsSettingsUiState()) },
                    onEnableDisable = {
                      updateState(
                        ShowingNotificationsSettingsUiState(
                          overlayState = AlertOverlayState(
                            alertModel = ButtonAlertModel(
                              title = "Are you sure you want to disable SMS recovery?",
                              subline = "The more recovery channels you have, the more secure your Bitkey is.",
                              onDismiss = {
                                updateState(ShowingNotificationsSettingsUiState())
                              },
                              primaryButtonText = "Disable SMS recovery",
                              onPrimaryButtonClick = {
                                updateState(
                                  DisablingNotificationChannelProofOfHwPossessionUiState(
                                    NotificationChannel.Sms
                                  )
                                )
                              },
                              primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
                              secondaryButtonText = "Keep SMS recovery",
                              onSecondaryButtonClick = {
                                updateState(ShowingNotificationsSettingsUiState())
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
            if (isCountryUS) {
              updateState(
                ShowingNotificationsSettingsUiState(
                  overlayState = BottomSheetOverlayState(
                    bottomSheetModel = SMSNonUSSheetModel(
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
      emailItem =
        RecoveryChannelsSettingsFormItemModel(
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
      alertModel = (stateVal.overlayState as? DelayedAlertOverlayState)?.alertModel,
      continueOnClick = null,
      onBack = props.onBack,
      learnOnClick = {
        updateState(ShowLearnRecoveryWebView)
      },
      bottomSheetModel = (stateVal.overlayState as? BottomSheetOverlayState)?.bottomSheetModel
    )
  }

  private fun PushToggleOverlay(
    pushRecoveryEnabled: Boolean,
    updateState: (RecoveryState) -> Unit,
  ): SheetModel {
    return PushToggleSheetModel(
      onCancel = { updateState(ShowingNotificationsSettingsUiState()) },
      isEnabled = pushRecoveryEnabled,
      onToggle = {
        if (pushRecoveryEnabled) {
          updateState(
            ShowingNotificationsSettingsUiState(
              overlayState =
                AlertOverlayState(
                  alertModel =
                    ButtonAlertModel(
                      title = "Are you sure you want to disable recovery push notifications?",
                      subline = "The more recovery channels you have, the more secure your Bitkey is.",
                      onDismiss = {
                        updateState(ShowingNotificationsSettingsUiState())
                      },
                      primaryButtonText = "Disable push notifications",
                      onPrimaryButtonClick = {
                        updateState(
                          DisablingNotificationChannelProofOfHwPossessionUiState(
                            NotificationChannel.Push
                          )
                        )
                      },
                      primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
                      secondaryButtonText = "Keep push notifications",
                      onSecondaryButtonClick = {
                        updateState(ShowingNotificationsSettingsUiState())
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
  ): String =
    when (this) {
      NotificationChannel.Email ->
        notificationTouchpointData?.email?.value
          ?: "(Email Address)"
      NotificationChannel.Push -> "Push Notification"
      NotificationChannel.Sms ->
        notificationTouchpointData?.phoneNumber?.formattedDisplayValue
          ?: "(SMS Number)"
    }.let { "Recovery channel $it will be disabled" }

  private sealed interface RecoveryState {
    /**
     * In editing state. If there are alerts or errors, other input should be disabled.
     */
    data class ShowingNotificationsSettingsUiState(
      val overlayState: OverlayState = OverlayState.None,
    ) : RecoveryState {
      sealed interface OverlayState {
        /** No overlaid info */
        data object None : OverlayState
      }

      /**
       * Loading should be extremely short because data should be cached, but it's not zero, and
       * it's *possible* data won't be in the cache. The UI needs to display the data in a way that
       * doesn't cause "incorrect flickering". For example, showing "Disabled" for a recovery channel
       * that is in fact enabled.
       */
      data object LoadingPreferencesOverlayState : OverlayState

      data class BottomSheetOverlayState(val bottomSheetModel: SheetModel) : OverlayState

      data class AlertOverlayState(val alertModel: ButtonAlertModel) : OverlayState

      /**
       * iOS seems unable to display an AlertModel while the bottom sheet closing animation
       * is still running. If there's a more formal way to handle this state, we can replace this.
       */
      data class DelayedAlertOverlayState(val alertModel: ButtonAlertModel) : OverlayState

      data object RequestingPushPermissionsOverlayState : OverlayState
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
