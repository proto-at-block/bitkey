package build.wallet.keybox.keys

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.encrypt.Secp256k1PublicKey
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppKeysGeneratorMock : AppKeysGenerator {
  private val defaultKeyBundleResult: Result<AppKeyBundle, Throwable> = Ok(AppKeyBundleMock)

  var keyBundleResult: Result<AppKeyBundle, Throwable> = defaultKeyBundleResult

  override suspend fun generateKeyBundle(
    network: BitcoinNetworkType,
  ): Result<AppKeyBundle, Throwable> {
    return keyBundleResult
  }

  var globalAuthKeyResult: Result<AppGlobalAuthPublicKey, Throwable> =
    Ok(AppGlobalAuthPublicKey(Secp256k1PublicKey("fake-auth-dpub")))

  override suspend fun generateGlobalAuthKey(): Result<AppGlobalAuthPublicKey, Throwable> {
    return globalAuthKeyResult
  }

  var recoveryAuthKeyResult: Result<AppRecoveryAuthPublicKey, Throwable> =
    Ok(AppRecoveryAuthPublicKeyMock)

  override suspend fun generateRecoveryAuthKey(): Result<AppRecoveryAuthPublicKey, Throwable> {
    return recoveryAuthKeyResult
  }

  fun reset() {
    keyBundleResult = defaultKeyBundleResult
    recoveryAuthKeyResult = Ok(AppRecoveryAuthPublicKeyMock)
  }
}
