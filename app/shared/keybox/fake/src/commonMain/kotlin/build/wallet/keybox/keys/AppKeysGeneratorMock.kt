package build.wallet.keybox.keys

import app.cash.turbine.Turbine
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.encrypt.Secp256k1PublicKey
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AppKeysGeneratorMock(
  turbine: (String) -> Turbine<Any>,
) : AppKeysGenerator {
  private val defaultKeyBundleResult: Result<AppKeyBundle, Throwable> =
    Ok(
      AppKeyBundle(
        localId = "fake-key-bundle-id",
        spendingKey = AppSpendingPublicKey(DescriptorPublicKeyMock("fake-spending-dpub")),
        authKey = AppGlobalAuthPublicKey(Secp256k1PublicKey("fake-auth-dpub")),
        networkType = SIGNET,
        recoveryAuthKey = AppRecoveryAuthPublicKeyMock
      )
    )

  var keyBundleResult = defaultKeyBundleResult
  val generateKeyBundleCalls = turbine("generateKeyBundle calls")

  override suspend fun generateKeyBundle(
    network: BitcoinNetworkType,
  ): Result<AppKeyBundle, Throwable> {
    generateKeyBundleCalls.add(Unit)
    return keyBundleResult
  }

  override suspend fun generateGlobalAuthKey(): Result<AppGlobalAuthPublicKey, Throwable> {
    return Ok(AppGlobalAuthPublicKey(Secp256k1PublicKey("fake-auth-dpub")))
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
