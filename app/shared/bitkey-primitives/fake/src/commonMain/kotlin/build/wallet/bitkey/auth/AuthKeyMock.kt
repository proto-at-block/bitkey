package build.wallet.bitkey.auth

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import okio.ByteString.Companion.encodeUtf8

val AppGlobalAuthPublicKeyMock =
  PublicKey<AppGlobalAuthKey>("app-auth-dpub")
val AppGlobalAuthPublicKeyMock2 =
  PublicKey<AppGlobalAuthKey>("app-auth-dpub-2")
val AppGlobalAuthPrivateKeyMock =
  PrivateKey<AppGlobalAuthKey>("app-auth-private-key".encodeUtf8())
val AppGlobalAuthKeypairMock = AppKey(AppGlobalAuthPublicKeyMock, AppGlobalAuthPrivateKeyMock)

val HwAuthSecp256k1PublicKeyMock = HwAuthPublicKey(Secp256k1PublicKey("hw-auth-dpub"))

val AppRecoveryAuthPublicKeyMock =
  PublicKey<AppRecoveryAuthKey>("app-recovery-auth-dpub")
val AppRecoveryAuthPublicKeyMock2 =
  PublicKey<AppRecoveryAuthKey>("app-recovery-auth-dpub-2")
val AppRecoveryAuthPrivateKeyMock =
  PrivateKey<AppRecoveryAuthKey>("app-recovery-auth-private-key".encodeUtf8())
val AppRecoveryAuthKeypairMock = AppKey(AppRecoveryAuthPublicKeyMock, AppRecoveryAuthPrivateKeyMock)

val HwAuthPublicKeyMock = HwAuthPublicKey(HwAuthSecp256k1PublicKeyMock.pubKey)
