package build.wallet.bitkey.socrec

import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthPublicKeyMock
import build.wallet.bitkey.auth.TcIdentityKeyAppSignatureMock
import build.wallet.bitkey.keys.app.AppKey

val TrustedContactKeyCertificateFake = TrustedContactKeyCertificate(
  delegatedDecryptionKey = DelegatedDecryptionKey(AppKey.fromPublicKey("deadbeef")),
  hwAuthPublicKey = HwAuthPublicKeyMock,
  appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
  appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
  trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignatureMock
)

val TrustedContactKeyCertificateFake2 = TrustedContactKeyCertificate(
  delegatedDecryptionKey = DelegatedDecryptionKey(AppKey.fromPublicKey("deadbeef-2")),
  hwAuthPublicKey = HwAuthPublicKeyMock,
  appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
  appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
  trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignatureMock
)
