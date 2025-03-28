package build.wallet.keybox.keys

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppKeysGeneratorMock : AppKeysGenerator {
  private val defaultKeyBundleResult: Result<AppKeyBundle, Throwable> = Ok(AppKeyBundleMock)

  var keyBundleResult: Result<AppKeyBundle, Throwable> = defaultKeyBundleResult

  override suspend fun generateKeyBundle(): Result<AppKeyBundle, Throwable> {
    return keyBundleResult
  }

  var globalAuthKeyResult: Result<PublicKey<AppGlobalAuthKey>, Throwable> =
    Ok(PublicKey("fake-auth-dpub"))

  override suspend fun generateGlobalAuthKey(): Result<PublicKey<AppGlobalAuthKey>, Throwable> {
    return globalAuthKeyResult
  }

  var recoveryAuthKeyResult: Result<PublicKey<AppRecoveryAuthKey>, Throwable> =
    Ok(AppRecoveryAuthPublicKeyMock)

  override suspend fun generateRecoveryAuthKey(): Result<PublicKey<AppRecoveryAuthKey>, Throwable> {
    return recoveryAuthKeyResult
  }

  fun reset() {
    keyBundleResult = defaultKeyBundleResult
    recoveryAuthKeyResult = Ok(AppRecoveryAuthPublicKeyMock)
  }
}
