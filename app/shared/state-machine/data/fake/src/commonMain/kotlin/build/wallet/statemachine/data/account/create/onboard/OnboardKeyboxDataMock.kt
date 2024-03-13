package build.wallet.statemachine.data.account.create.onboard

import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull
import okio.ByteString

val BackingUpKeyboxToCloudDataMock =
  OnboardKeyboxDataFull.BackingUpKeyboxToCloudDataFull(
    keybox = KeyboxMock,
    sealedCsek = ByteString.EMPTY,
    onBackupSaved = {},
    onBackupFailed = {},
    isSkipCloudBackupInstructions = false
  )

val SettingNotificationsPreferencesDataMock =
  OnboardKeyboxDataFull.SettingNotificationsPreferencesDataFull(
    keybox = KeyboxMock,
    onComplete = {}
  )
