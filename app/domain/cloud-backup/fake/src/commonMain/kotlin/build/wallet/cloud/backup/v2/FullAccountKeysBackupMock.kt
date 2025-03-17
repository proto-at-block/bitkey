package build.wallet.cloud.backup.v2

import build.wallet.bitkey.auth.AppGlobalAuthPrivateKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthPublicKeyMock
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.spending.AppSpendingPrivateKeyMock
import build.wallet.bitkey.spending.AppSpendingPublicKeyMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2

val FullAccountKeysMock =
  FullAccountKeys(
    activeSpendingKeyset = SpendingKeysetMock,
    activeHwSpendingKey = HwSpendingPublicKeyMock,
    activeHwAuthKey = HwAuthPublicKeyMock,
    appGlobalAuthKeypair =
      AppKey(
        AppGlobalAuthPublicKeyMock,
        AppGlobalAuthPrivateKeyMock
      ),
    inactiveSpendingKeysets =
      listOf(
        SpendingKeysetMock2
      ),
    appSpendingKeys = mapOf(AppSpendingPublicKeyMock to AppSpendingPrivateKeyMock),
    rotationAppGlobalAuthKeypair = null
  )
