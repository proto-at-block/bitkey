package build.wallet.keybox.keys

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import com.github.michaelbull.result.Result
import com.russhwolf.settings.ExperimentalSettingsApi

private const val KEYSTORE_KEY = "onboarding-app-keys"
private const val SPENDING_KEY = "spending_key"
private const val APP_GLOBAL_AUTH_KEY = "auth_key"
private const val APP_RECOVERY_AUTH_KEY = "app_recovery_auth_key"
private const val NETWORK_KEY = "network"

/**
 * This implementation of [OnboardingAppKeyKeystore] uses [SuspendSettings] to persist app keys
 * during onboarding to allow the data to survive past uninstalls. *note* this behavior only happens
 * on iOS currently
 *
 * @param encryptedKeyValueStoreFactory - factory for creating a store
 */
@OptIn(ExperimentalSettingsApi::class)
class OnboardingAppKeyKeystoreImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : OnboardingAppKeyKeystore {
  private suspend fun appKeystore() = encryptedKeyValueStoreFactory.getOrCreate(KEYSTORE_KEY)

  override suspend fun persistAppKeys(
    spendingKey: AppSpendingPublicKey,
    globalAuthKey: AppGlobalAuthPublicKey,
    recoveryAuthKey: AppRecoveryAuthPublicKey,
    bitcoinNetworkType: BitcoinNetworkType,
  ) {
    val appKeystore = appKeystore()
    appKeystore.putString(SPENDING_KEY, spendingKey.key.dpub)
    appKeystore.putString(APP_GLOBAL_AUTH_KEY, globalAuthKey.pubKey.value)
    appKeystore.putString(APP_RECOVERY_AUTH_KEY, recoveryAuthKey.pubKey.value)
    appKeystore.putString(NETWORK_KEY, bitcoinNetworkType.name)
  }

  override suspend fun getAppKeyBundle(
    localId: String,
    network: BitcoinNetworkType,
  ): AppKeyBundle? {
    val appKeystore = appKeystore()
    return if (appKeystore.hasKey(SPENDING_KEY) && appKeystore.hasKey(APP_GLOBAL_AUTH_KEY)) {
      val spendingKey = appKeystore.getStringOrNull(SPENDING_KEY)
      val globalAuthKey = appKeystore.getStringOrNull(APP_GLOBAL_AUTH_KEY)
      val recoveryAuthKey = appKeystore.getStringOrNull(APP_RECOVERY_AUTH_KEY)
      val networkType =
        when (val networkName = appKeystore.getStringOrNull(NETWORK_KEY)) {
          null -> null
          else -> BitcoinNetworkType.valueOf(networkName)
        }

      if (spendingKey == null || globalAuthKey == null || network != networkType) {
        null
      } else {
        AppKeyBundle(
          localId = localId,
          spendingKey = AppSpendingPublicKey(spendingKey),
          authKey = AppGlobalAuthPublicKey(Secp256k1PublicKey(globalAuthKey)),
          networkType = network,
          recoveryAuthKey =
            recoveryAuthKey?.let {
              // It's possible that an existing customer has already generated app keys, but not the
              // recovery auth key, prior the app update that started generating the recovery auth key.
              // In this case, we don't know have the recovery auth key generated; it will be backfilled
              // later (TODO: BKR-573).
              AppRecoveryAuthPublicKey(Secp256k1PublicKey(recoveryAuthKey))
            }
        )
      }
    } else {
      null
    }
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    return appKeystore().clearWithResult()
  }
}
