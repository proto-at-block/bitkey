package build.wallet.keybox

import bitkey.account.FullAccountConfig
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.database.sqldelight.FullAccountView
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import build.wallet.mapResult
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.*

@BitkeyInject(AppScope::class)
class KeyboxDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : KeyboxDao {
  override fun activeKeybox(): Flow<Result<Keybox?, DbError>> {
    return flow {
      databaseProvider.database()
        .fullAccountQueries
        .getActiveFullAccount()
        .asFlowOfOneOrNull()
        .mapResult { it?.fullAccount()?.keybox }
        .distinctUntilChanged()
        .collect(::emit)
    }
  }

  override fun onboardingKeybox(): Flow<Result<Keybox?, DbError>> {
    return flow {
      databaseProvider.database()
        .fullAccountQueries
        .getOnboardingFullAccount()
        .asFlowOfOneOrNull()
        .mapResult { it?.fullAccount()?.keybox }
        .distinctUntilChanged()
        .collect(::emit)
    }
  }

  override suspend fun getActiveOrOnboardingKeybox(): Result<Keybox?, DbError> {
    return activeKeybox().first()
      .map { value ->
        value ?: onboardingKeybox().first().value
      }
  }

  override suspend fun saveKeyboxAsActive(keybox: Keybox): Result<Unit, DbError> {
    logDebug { "Saving keybox as active: $keybox" }
    return databaseProvider.database()
      .awaitTransaction {
        saveKeybox(keybox)
        fullAccountQueries.setActiveFullAccountId(keybox.fullAccountId)
      }
      .logFailure { "Failed to save keybox" }
  }

  override suspend fun saveKeyboxAndBeginOnboarding(keybox: Keybox): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        saveKeybox(keybox)
        fullAccountQueries.setOnboardingFullAccountId(keybox.fullAccountId)
      }
      .logFailure { "Failed to save keybox" }
  }

  override suspend fun activateNewKeyboxAndCompleteOnboarding(
    keybox: Keybox,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        fullAccountQueries.setActiveFullAccountId(keybox.fullAccountId)
        fullAccountQueries.clearOnboardingFullAccount()
        liteAccountQueries.clear()
      }
      .logFailure { "Failed to activate keybox" }
  }

  override suspend fun rotateKeyboxAuthKeys(
    keyboxToRotate: Keybox,
    appAuthKeys: AppAuthPublicKeys,
  ): Result<Keybox, DbError> {
    return databaseProvider.database()
      .awaitTransactionWithResult {
        keyboxQueries.rotateAppGlobalAuthKeyHwSignature(
          id = keyboxToRotate.localId,
          appGlobalAuthKeyHwSignature = appAuthKeys.appGlobalAuthKeyHwSignature
        )
        appKeyBundleQueries.rotateAppAuthKeys(
          globalAuthKey = appAuthKeys.appGlobalAuthPublicKey,
          recoveryAuthKey = appAuthKeys.appRecoveryAuthPublicKey,
          id = keyboxToRotate.activeAppKeyBundle.localId
        )

        keyboxToRotate.copy(
          activeAppKeyBundle = keyboxToRotate.activeAppKeyBundle.copy(
            authKey = appAuthKeys.appGlobalAuthPublicKey,
            recoveryAuthKey = appAuthKeys.appRecoveryAuthPublicKey
          ),
          appGlobalAuthKeyHwSignature = appAuthKeys.appGlobalAuthKeyHwSignature
        )
      }
      .logFailure { "Failed to rotate app auth keys" }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        fullAccountQueries.clear()
        keyboxQueries.clear()
        spendingKeysetQueries.clear()
        appKeyBundleQueries.clear()
        hwKeyBundleQueries.clear()
      }
      .logFailure { "Failed to clear bitcoin database" }
  }

  private fun BitkeyDatabase.saveKeybox(keybox: Keybox) {
    spendingKeysetQueries.insertKeyset(
      id = keybox.activeSpendingKeyset.localId,
      serverId = keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
      appKey = keybox.activeSpendingKeyset.appKey,
      hardwareKey = keybox.activeSpendingKeyset.hardwareKey,
      serverKey = keybox.activeSpendingKeyset.f8eSpendingKeyset.spendingPublicKey
    )
    // Insert the app key bundle
    appKeyBundleQueries.insertKeyBundle(
      id = keybox.activeAppKeyBundle.localId,
      globalAuthKey = keybox.activeAppKeyBundle.authKey,
      spendingKey = keybox.activeAppKeyBundle.spendingKey,
      recoveryAuthKey = keybox.activeAppKeyBundle.recoveryAuthKey
    )

    // Insert the hw key bundle
    hwKeyBundleQueries.insertKeyBundle(
      id = keybox.activeHwKeyBundle.localId,
      authKey = keybox.activeHwKeyBundle.authKey,
      spendingKey = keybox.activeHwKeyBundle.spendingKey
    )

    // Insert the keybox
    keyboxQueries.insertKeybox(
      id = keybox.localId,
      account = keybox.fullAccountId,
      activeSpendingKeysetId = keybox.activeSpendingKeyset.localId,
      activeKeyBundleId = keybox.activeAppKeyBundle.localId,
      activeHwKeyBundleId = keybox.activeHwKeyBundle.localId,
      appGlobalAuthKeyHwSignature = keybox.appGlobalAuthKeyHwSignature,
      networkType = keybox.config.bitcoinNetworkType,
      fakeHardware = keybox.config.isHardwareFake,
      f8eEnvironment = keybox.config.f8eEnvironment,
      isTestAccount = keybox.config.isTestAccount,
      isUsingSocRecFakes = keybox.config.isUsingSocRecFakes,
      delayNotifyDuration = keybox.config.delayNotifyDuration
    )

    // Insert full account
    fullAccountQueries.insertFullAccount(
      accountId = keybox.fullAccountId,
      keyboxId = keybox.localId
    )
  }

  private suspend fun FullAccountView.fullAccount(): FullAccount {
    val keybox = keybox()
    return FullAccount(
      accountId = accountId,
      config = keybox.config,
      keybox = keybox
    )
  }

  private suspend fun FullAccountView.keybox(): Keybox {
    return Keybox(
      localId = keyboxId,
      fullAccountId = accountId,
      activeSpendingKeyset =
        SpendingKeyset(
          localId = spendingPublicKeysetId,
          f8eSpendingKeyset =
            F8eSpendingKeyset(
              keysetId = spendingPublicKeysetServerId,
              spendingPublicKey = serverKey
            ),
          appKey = appKey,
          hardwareKey = hardwareKey,
          networkType = networkType
        ),
      activeAppKeyBundle =
        AppKeyBundle(
          localId = appKeyBundleId,
          spendingKey = appKey,
          authKey = globalAuthKey,
          networkType = networkType,
          recoveryAuthKey = recoveryAuthKey
        ),
      activeHwKeyBundle = HwKeyBundle(
        localId = hwKeyBundleId,
        spendingKey = hwSpendingKey,
        authKey = hwAuthKey,
        networkType = networkType
      ),
      appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature,
      config =
        FullAccountConfig(
          bitcoinNetworkType = networkType,
          isHardwareFake = fakeHardware,
          f8eEnvironment = f8eEnvironment,
          isTestAccount = isTestAccount,
          isUsingSocRecFakes = isUsingSocRecFakes,
          delayNotifyDuration = delayNotifyDuration
        )
    )
  }
}
