package build.wallet.bitkey.relationships

import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthPublicKeyMock
import build.wallet.bitkey.auth.TcIdentityKeyAppSignatureMock
import build.wallet.crypto.PublicKey

val TrustedContactKeyCertificateFake = TrustedContactKeyCertificate(
  delegatedDecryptionKey = PublicKey("deadbeef"),
  hwAuthPublicKey = HwAuthPublicKeyMock,
  appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
  appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
  trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignatureMock
)

val TrustedContactKeyCertificateFake2 = TrustedContactKeyCertificate(
  delegatedDecryptionKey = PublicKey("deadbeef-2"),
  hwAuthPublicKey = HwAuthPublicKeyMock,
  appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
  appAuthGlobalKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
  trustedContactIdentityKeyAppSignature = TcIdentityKeyAppSignatureMock
)
