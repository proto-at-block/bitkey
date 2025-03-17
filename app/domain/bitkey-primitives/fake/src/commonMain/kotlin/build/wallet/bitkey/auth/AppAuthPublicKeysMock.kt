package build.wallet.bitkey.auth

import build.wallet.bitkey.app.AppAuthPublicKeys

val AppAuthPublicKeysMock = AppAuthPublicKeys(
  appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
  appRecoveryAuthPublicKey = AppRecoveryAuthPublicKeyMock,
  appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock
)
