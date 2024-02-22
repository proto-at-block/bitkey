package build.wallet.keybox.keys

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingAppKeyKeystoreFake : OnboardingAppKeyKeystore {
  var appKeys: AppKeyBundle? = null

  override suspend fun persistAppKeys(
    spendingKey: AppSpendingPublicKey,
    globalAuthKey: AppGlobalAuthPublicKey,
    recoveryAuthKey: AppRecoveryAuthPublicKey,
    bitcoinNetworkType: BitcoinNetworkType,
  ) {
    appKeys =
      AppKeyBundle(
        localId = "",
        spendingKey = spendingKey,
        authKey = globalAuthKey,
        networkType = bitcoinNetworkType,
        recoveryAuthKey = AppRecoveryAuthPublicKeyMock
      )
  }

  override suspend fun getAppKeyBundle(
    localId: String,
    network: BitcoinNetworkType,
  ): AppKeyBundle? {
    return appKeys?.copy(localId = localId, networkType = network)
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    appKeys = null
    return Ok(Unit)
  }
}
