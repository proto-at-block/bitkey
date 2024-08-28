package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.statemachine.data.account.create.onboard.BackingUpKeyboxToCloudDataMock
import build.wallet.statemachine.data.account.create.onboard.SettingNotificationsPreferencesDataMock
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData

val ActiveKeyboxLoadedDataMock = ActiveFullAccountLoadedData(
  account = FullAccountMock,
  lostHardwareRecoveryData = AwaitingNewHardwareData(
    newAppGlobalAuthKey = AppGlobalAuthPublicKeyMock,
    addHardwareKeys = { _, _, _ -> }
  )
)

fun OnboardingKeyboxDataMock(inProgressStep: OnboardingKeyboxStep = CloudBackup) =
  when (inProgressStep) {
    CloudBackup -> BackingUpKeyboxToCloudDataMock
    NotificationPreferences -> SettingNotificationsPreferencesDataMock
  }
