package build.wallet.cloud.backup.v2

import build.wallet.bitkey.auth.AppGlobalAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthPublicKeyMock
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock

val FullAccountKeysMock = FullAccountKeys(
  activeSpendingKeyset = SpendingKeysetMock,
  keysets = listOf(SpendingKeysetMock),
  activeHwSpendingKey = HwSpendingPublicKeyMock,
  activeHwAuthKey = HwAuthPublicKeyMock,
  appGlobalAuthKeypair = AppKey(
    AppGlobalAuthPublicKeyMock,
    AppGlobalAuthPrivateKeyMock
  ),
  appSpendingKeys = mapOf(AppSpendingPublicKeyMock to AppSpendingPrivateKeyMock),
  rotationAppGlobalAuthKeypair = null
)
