package build.wallet.keybox.keys

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class OnboardingAppKeyKeystoreFake : OnboardingAppKeyKeystore {
  var appKeys: AppKeyBundle? = null

  override suspend fun persistAppKeys(
    spendingKey: AppSpendingPublicKey,
    globalAuthKey: PublicKey<AppGlobalAuthKey>,
    recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
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
