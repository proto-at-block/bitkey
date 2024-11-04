package build.wallet.statemachine.notifications

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.ktor.result.NetworkingError
import build.wallet.notifications.NotificationChannel
import build.wallet.notifications.NotificationPreferences
import build.wallet.platform.permissions.Permission
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.account.create.full.onboard.notifications.openSettingsForPushAlertModel
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.platform.permissions.NotificationPermissionRequester
import build.wallet.ui.model.label.CallToActionModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

class NotificationPreferencesUiStateMachineImpl(
  private val permissionChecker: PermissionChecker,
  private val notificationsPreferencesCachedProvider: NotificationsPreferencesCachedProvider,
  private val systemSettingsLauncher: SystemSettingsLauncher,
  private val notificationPermissionRequester: NotificationPermissionRequester,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val eventTracker: EventTracker,
) : NotificationPreferencesUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: NotificationPreferencesProps): ScreenModel {
    val shouldLoadSavedPreferences = props.source == NotificationPreferencesProps.Source.Settings

    var uiState: UiState by remember {
      mutableStateOf(
        if (shouldLoadSavedPreferences) {
          UiState.MainViewState.Loading
        } else {
          UiState.MainViewState.Editing
        }
      )
    }

    val scope = rememberStableCoroutineScope()
    var transactionPush by remember { mutableStateOf(false) }
    var updatesPush by remember { mutableStateOf(false) }
    var updatesEmail by remember { mutableStateOf(false) }
    var termsAgree by remember {
      mutableStateOf(props.source == NotificationPreferencesProps.Source.Settings)
    }
    var currentPreferences by remember {
      mutableStateOf(
        NotificationPreferences(
          accountSecurity = props.onboardingRecoveryChannelsEnabled,
          productMarketing = emptySet(),
          moneyMovement = emptySet()
        )
      )
    }

    LaunchedEffect("load-push-and-preferences-state") {
      // If we're coming from settings, load user's settings from the server
      if (shouldLoadSavedPreferences) {
        notificationsPreferencesCachedProvider.getNotificationsPreferences(
          props.f8eEnvironment,
          props.accountId
        )
          // Generally speaking, there will be only one result, coming from the cache.
          // However, if the server state differs from the cache, we'll emit a second
          // data set for the UI.
          .collect {
            it.onSuccess { prefs ->
              currentPreferences = prefs
              transactionPush = prefs.moneyMovement.contains(NotificationChannel.Push)
              updatesPush = prefs.productMarketing.contains(NotificationChannel.Push)
              updatesEmail = prefs.productMarketing.contains(NotificationChannel.Email)
              uiState = UiState.MainViewState.Editing
            }.onFailure { error ->
              // If we have cache data, the provider doesn't signal an error if the server
              // check fails. We'll only get an "error" if there's no cached data, and
              // the server refresh call fails.
              uiState = UiState.MainViewState.NetworkError(
                networkingError = error,
                onClose = props.onBack
              )
            }
          }
      } else {
        uiState = UiState.MainViewState.Editing
      }
    }

    return when (uiState) {
      is UiState.BrowserViewState -> {
        openBrowser(
          browserState = uiState as UiState.BrowserViewState,
          setUiState = { uiState = it }
        )
      }

      is UiState.MainViewState -> {
        // If the user requests push toggle and they haven't explicitly denied it yet, we show the
        // system request and hold a reference to the operation they were attempting when the popup
        // was shown. If granted, we continue with the operation (specifically, flipping the toggle).
        // If the request was previously denied, the user needs to open settings. We show a dialog for that,
        // but lose context when switching apps. Attempting to hold onto that state would be difficult.
        (uiState as? UiState.MainViewState.EditingWithOverlay.SystemPromptRequestingPush)?.let {
            state ->
          notificationPermissionRequester.requestNotificationPermission(
            onGranted = {
              eventTracker.track(Action.ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
              state.onPushActivate.invoke()
              uiState = UiState.MainViewState.Editing
            },
            onDeclined = {
              eventTracker.track(Action.ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
              uiState = UiState.MainViewState.Editing
            }
          )
        }

        val onPushToggle: (Boolean, () -> Unit) -> Unit = { isEnabled, toggleAction ->
          when (permissionChecker.getPermissionStatus(Permission.PushNotifications)) {
            PermissionStatus.NotDetermined -> {
              if (isEnabled) {
                uiState =
                  UiState.MainViewState.EditingWithOverlay.SystemPromptRequestingPush(onPushActivate = toggleAction)
              } else {
                toggleAction()
              }
            }
            PermissionStatus.Denied -> {
              if (isEnabled) {
                uiState = UiState.MainViewState.EditingWithOverlay.OpenSettings
              } else {
                toggleAction()
              }
            }
            PermissionStatus.Authorized -> {
              toggleAction()
            }
          }
        }

        val formEditingState: NotificationPreferencesFormEditingState =
          when (uiState as UiState.MainViewState) {
            is UiState.MainViewState.Editing,
            is UiState.MainViewState.DidNotSelectToS,
            -> NotificationPreferencesFormEditingState.Editing
            UiState.MainViewState.Loading -> NotificationPreferencesFormEditingState.Loading
            is UiState.MainViewState.EditingWithOverlay,
            is UiState.MainViewState.NetworkError,
            -> NotificationPreferencesFormEditingState.Overlay
          }

        NotificationPreferenceFormBodyModel(
          transactionPush = transactionPush,
          updatesPush = updatesPush,
          updatesEmail = updatesEmail,
          tosInfo = TosInfo(
            termsAgree = termsAgree,
            onTermsAgreeToggle = {
              if (!termsAgree && uiState is UiState.MainViewState.DidNotSelectToS) {
                uiState = UiState.MainViewState.Editing
              }

              termsAgree = it
            },
            tosLink = { uiState = UiState.BrowserViewState.TosView },
            privacyLink = { uiState = UiState.BrowserViewState.PrivacyView }
          ).takeIf { props.source == NotificationPreferencesProps.Source.Onboarding },
          onTransactionPushToggle = { active ->
            onPushToggle(active) { transactionPush = active }
          },
          onUpdatesPushToggle = { active ->
            onPushToggle(active) { updatesPush = active }
          },
          onUpdatesEmailToggle = { updatesEmail = it },
          formEditingState = formEditingState,
          onBack = props.onBack,
          ctaModel = when (uiState) {
            is UiState.MainViewState.DidNotSelectToS -> CallToActionModel(
              text = "Agree to our Terms and Privacy Policy to continue.",
              treatment = CallToActionModel.Treatment.WARNING
            )
            else -> null
          },
          continueOnClick = {
            if (!termsAgree) {
              uiState = UiState.MainViewState.DidNotSelectToS
            } else {
              uiState = UiState.MainViewState.Loading
              scope.launch {
                val np = currentPreferences.copy(
                  moneyMovement = setOfNotNull(NotificationChannel.Push.takeIf { transactionPush }),
                  productMarketing = setOfNotNull(
                    NotificationChannel.Push.takeIf { updatesPush },
                    NotificationChannel.Email.takeIf { updatesEmail }
                  )
                )

                sendNotificationPreferences(
                  props = props,
                  notificationPreferences = np,
                  setUiState = { uiState = it }
                )
              }
            }
          }
        ).asRootScreen(
          alertModel = openSettingsForPushAlertModel(
            pushEnabled = false,
            settingsOpenAction = {
              systemSettingsLauncher.launchAppSettings()
              uiState = UiState.MainViewState.Editing
            },
            onClose = { uiState = UiState.MainViewState.Editing }
          ).takeIf {
            uiState is UiState.MainViewState.EditingWithOverlay.OpenSettings
          },
          bottomSheetModel = (uiState as? UiState.MainViewState.NetworkError)?.let { errorState ->
            NetworkingErrorSheetModel(
              onClose = errorState.onClose,
              networkingError = errorState.networkingError
            )
          }
        )
      }
    }
  }

  /**
   * Open external browser links
   */
  private fun openBrowser(
    browserState: UiState.BrowserViewState,
    setUiState: (UiState) -> Unit,
  ): ScreenModel =
    when (browserState) {
      UiState.BrowserViewState.PrivacyView -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://bitkey.world/en-US/legal/privacy-notice",
              onClose = {
                setUiState(UiState.MainViewState.Editing)
              }
            )
          }
        ).asModalScreen()
      }
      UiState.BrowserViewState.TosView -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://bitkey.world/en-US/legal/terms-of-service",
              onClose = {
                setUiState(UiState.MainViewState.Editing)
              }
            )
          }
        ).asModalScreen()
      }
    }

  private suspend fun sendNotificationPreferences(
    props: NotificationPreferencesProps,
    notificationPreferences: NotificationPreferences,
    setUiState: (UiState) -> Unit,
  ) {
    notificationsPreferencesCachedProvider.updateNotificationsPreferences(
      props.f8eEnvironment,
      props.accountId,
      notificationPreferences,
      null
    ).onSuccess {
      notificationPreferences.moneyMovement.forEach {
        when (it) {
          NotificationChannel.Push -> eventTracker.track(Action.ACTION_APP_TRANSACTION_PUSH_NOTIFICATIONS_ENABLED)
          else -> {}
        }
      }
      notificationPreferences.productMarketing.forEach {
        when (it) {
          NotificationChannel.Email -> eventTracker.track(Action.ACTION_APP_MARKETING_EMAIL_NOTIFICATIONS_ENABLED)
          NotificationChannel.Push -> eventTracker.track(Action.ACTION_APP_MARKETING_PUSH_NOTIFICATIONS_ENABLED)
          else -> {}
        }
      }
      setUiState(UiState.MainViewState.Editing)
      props.onComplete()
    }.onFailure { error ->
      setUiState(
        UiState.MainViewState.NetworkError(
          networkingError = error,
          onClose = { setUiState(UiState.MainViewState.Editing) }
        )
      )
    }
  }

  private sealed interface UiState {
    sealed interface MainViewState : UiState {
      /**
       * Loading preferences from the server. Only needed for settings. Onboarding should have these defaulted.
       */
      data object Loading : MainViewState

      data object Editing : MainViewState

      /**
       * Loaded and editing.
       */
      sealed interface EditingWithOverlay : MainViewState {
        data object OpenSettings : EditingWithOverlay

        data class SystemPromptRequestingPush(val onPushActivate: () -> Unit) : EditingWithOverlay
      }

      /**
       * User attempted to continue without accepting ToS
       */
      data object DidNotSelectToS : MainViewState

      /**
       * Network error. If on load, back action returns user to settings. If on save, return to editing
       * and user can try again.
       */
      data class NetworkError(val networkingError: NetworkingError, val onClose: () -> Unit) :
        MainViewState
    }

    sealed interface BrowserViewState : UiState {
      data object TosView : BrowserViewState

      data object PrivacyView : BrowserViewState
    }
  }
}
