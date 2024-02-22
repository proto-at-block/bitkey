package build.wallet.statemachine.data.keybox

import app.cash.turbine.Turbine
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.CurrencyPreference
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.statemachine.data.account.create.onboard.BackingUpKeyboxToCloudDataMock
import build.wallet.statemachine.data.account.create.onboard.SettingCurrencyPreferenceDataMock
import build.wallet.statemachine.data.account.create.onboard.SettingNotificationsPreferencesDataMock
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.keybox.address.KeyboxAddressDataMock
import build.wallet.statemachine.data.keybox.transactions.KeyboxTransactionsDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayEnabledDataMock
import build.wallet.statemachine.data.notifications.NotificationTouchpointDataMock
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData

val ActiveKeyboxLoadedDataMock =
  ActiveFullAccountLoadedData(
    account = FullAccountMock,
    spendingWallet = SpendingWalletMock({ Turbine() }, KeyboxMock.activeSpendingKeyset.localId),
    addressData = KeyboxAddressDataMock,
    transactionsData = KeyboxTransactionsDataMock,
    mobilePayData = MobilePayEnabledDataMock,
    lostHardwareRecoveryData =
      AwaitingNewHardwareData(
        addHardwareKeys = { _, _ -> }
      ),
    notificationTouchpointData = NotificationTouchpointDataMock,
    isCompletingSocialRecovery = false
  )

fun OnboardingKeyboxDataMock(inProgressStep: OnboardingKeyboxStep = CloudBackup) =
  when (inProgressStep) {
    CloudBackup -> BackingUpKeyboxToCloudDataMock
    NotificationPreferences -> SettingNotificationsPreferencesDataMock
    CurrencyPreference -> SettingCurrencyPreferenceDataMock
  }
