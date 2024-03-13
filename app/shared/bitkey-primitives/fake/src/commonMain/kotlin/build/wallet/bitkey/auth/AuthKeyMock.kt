package build.wallet.bitkey.auth

import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppGlobalAuthPrivateKey
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import build.wallet.bitkey.app.AppRecoveryAuthPrivateKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.encrypt.Secp256k1PublicKey
import okio.ByteString.Companion.encodeUtf8

val AppGlobalAuthPublicKeyMock =
  AppGlobalAuthPublicKey(pubKey = Secp256k1PublicKey("app-auth-dpub"))
val AppGlobalAuthPublicKeyMock2 =
  AppGlobalAuthPublicKey(pubKey = Secp256k1PublicKey("app-auth-dpub-2"))
val AppGlobalAuthPrivateKeyMock =
  AppGlobalAuthPrivateKey(Secp256k1PrivateKey("app-auth-private-key".encodeUtf8()))
val AppGlobalAuthKeypairMock =
  AppGlobalAuthKeypair(AppGlobalAuthPublicKeyMock, AppGlobalAuthPrivateKeyMock)

val HwAuthSecp256k1PublicKeyMock = HwAuthPublicKey(Secp256k1PublicKey("hw-auth-dpub"))

val AppRecoveryAuthPublicKeyMock =
  AppRecoveryAuthPublicKey(Secp256k1PublicKey("app-recovery-auth-dpub"))
val AppRecoveryAuthPublicKeyMock2 =
  AppRecoveryAuthPublicKey(Secp256k1PublicKey("app-recovery-auth-dpub-2"))
val AppRecoveryAuthPrivateKeyMock =
  AppRecoveryAuthPrivateKey(Secp256k1PrivateKey("app-recovery-auth-private-key".encodeUtf8()))
val AppRecoveryAuthKeypairMock =
  AppRecoveryAuthKeypair(AppRecoveryAuthPublicKeyMock, AppRecoveryAuthPrivateKeyMock)

val HwAuthPublicKeyMock = HwAuthPublicKey(HwAuthSecp256k1PublicKeyMock.pubKey)
