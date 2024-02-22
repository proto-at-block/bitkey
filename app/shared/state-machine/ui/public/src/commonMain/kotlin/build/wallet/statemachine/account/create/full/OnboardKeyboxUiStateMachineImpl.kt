package build.wallet.statemachine.account.create.full

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILED
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_LOADING
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.analytics.events.screen.id.CurrencyEventTrackerScreenId.SAVE_CURRENCY_PREFERENCE_LOADING
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.SAVE_NOTIFICATIONS_LOADING
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiProps
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachine
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.BackingUpKeyboxToCloudDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingCloudBackupDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingCurrencyPreferenceDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.CompletingNotificationsDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.FailedCloudBackupDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.SettingCurrencyPreferenceDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull.SettingNotificationsPreferencesDataFull
import build.wallet.statemachine.money.currency.CurrencyPreferenceProps
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiStateMachine

class OnboardKeyboxUiStateMachineImpl(
  private val fullAccountCloudSignInAndBackupUiStateMachine:
    FullAccountCloudSignInAndBackupUiStateMachine,
  private val currencyPreferenceUiStateMachine: CurrencyPreferenceUiStateMachine,
  private val notificationPreferencesSetupUiStateMachine:
    NotificationPreferencesSetupUiStateMachine,
) : OnboardKeyboxUiStateMachine {
  @Composable
  override fun model(props: OnboardKeyboxUiProps): ScreenModel {
    return when (props.onboardKeyboxData) {
      is CreateFullAccountData.OnboardKeyboxDataFull.LoadingInitialStepDataFull ->
        LoadingBodyModel(id = LOADING_ONBOARDING_STEP).asRootScreen()

      is BackingUpKeyboxToCloudDataFull ->
        BackingUpKeyboxScreen(props.onboardKeyboxData)

      is FailedCloudBackupDataFull ->
        FailedCloudBackupScreen(props.onboardKeyboxData)

      is CompletingCloudBackupDataFull ->
        LoadingBodyModel(id = SAVE_CLOUD_BACKUP_LOADING).asRootScreen()

      is SettingNotificationsPreferencesDataFull ->
        SettingNotificationsPreferencesScreen(props.onboardKeyboxData)

      is CompletingNotificationsDataFull ->
        LoadingBodyModel(id = SAVE_NOTIFICATIONS_LOADING).asRootScreen()

      is SettingCurrencyPreferenceDataFull ->
        SettingCurrencyPreferenceScreen(props.onboardKeyboxData)

      is CompletingCurrencyPreferenceDataFull ->
        LoadingBodyModel(id = SAVE_CURRENCY_PREFERENCE_LOADING).asRootScreen()
    }
  }

  @Composable
  private fun BackingUpKeyboxScreen(data: BackingUpKeyboxToCloudDataFull): ScreenModel {
    return fullAccountCloudSignInAndBackupUiStateMachine.model(
      props =
        FullAccountCloudSignInAndBackupProps(
          sealedCsek = data.sealedCsek,
          keybox = data.keybox,
          trustedContacts = emptyList(),
          onBackupFailed = data.onBackupFailed,
          onBackupSaved = data.onBackupSaved,
          onExistingCloudBackupFound = data.onExistingCloudBackupFound,
          presentationStyle = ScreenPresentationStyle.Root,
          isSkipCloudBackupInstructions = data.isSkipCloudBackupInstructions
        )
    )
  }

  @Composable
  fun FailedCloudBackupScreen(data: FailedCloudBackupDataFull): ScreenModel {
    return CloudBackupFailedScreenModel(
      eventTrackerScreenId = SAVE_CLOUD_BACKUP_FAILED,
      onTryAgain = data.retry
    ).asRootScreen()
  }

  @Composable
  private fun SettingNotificationsPreferencesScreen(
    data: SettingNotificationsPreferencesDataFull,
  ): ScreenModel {
    return notificationPreferencesSetupUiStateMachine.model(
      props =
        NotificationPreferencesSetupUiProps(
          fullAccountId = data.keybox.fullAccountId,
          keyboxConfig = data.keybox.config,
          onComplete = data.onComplete
        )
    )
  }

  @Composable
  private fun SettingCurrencyPreferenceScreen(
    data: SettingCurrencyPreferenceDataFull,
  ): ScreenModel {
    return currencyPreferenceUiStateMachine.model(
      props =
        CurrencyPreferenceProps(
          onBack = null,
          // Use hard-coded 0 amount to display because we're in onboarding
          // (post-onboarding we use balance amount)
          btcDisplayAmount = BitcoinMoney.zero(),
          currencyPreferenceData = data.currencyPreferenceData,
          onDone = data.onComplete
        )
    )
  }
}
