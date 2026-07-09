package build.wallet.keybox

import bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.database.sqldelight.FullAccountView
import build.wallet.database.sqldelight.saveKeybox
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
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
        .map { it.flatMap { fullAccount -> fullAccount?.keybox() ?: Ok(null) } }
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
        .map { it.flatMap { fullAccount -> fullAccount?.keybox() ?: Ok(null) } }
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
    newHwAuthPublicKey: HwAuthPublicKey?,
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

        if (newHwAuthPublicKey != null) {
          hwKeyBundleQueries.updateAuthKeyForActiveBundle(
            authKey = newHwAuthPublicKey,
            keyboxId = keyboxToRotate.localId
          )
        }

        keyboxToRotate.copy(
          activeAppKeyBundle = keyboxToRotate.activeAppKeyBundle.copy(
            authKey = appAuthKeys.appGlobalAuthPublicKey,
            recoveryAuthKey = appAuthKeys.appRecoveryAuthPublicKey
          ),
          appGlobalAuthKeyHwSignature = appAuthKeys.appGlobalAuthKeyHwSignature,
          activeHwKeyBundle = keyboxToRotate.activeHwKeyBundle.copy(
            authKey = newHwAuthPublicKey ?: keyboxToRotate.activeHwKeyBundle.authKey
          )
        )
      }
      .logFailure { "Failed to rotate auth keys" }
  }

  override suspend fun updateAppGlobalAuthKeyHwSignature(
    keybox: Keybox,
    signature: AppGlobalAuthKeyHwSignature,
  ): Result<Keybox, DbError> =
    coroutineBinding {
      val db = databaseProvider.database()
      db.awaitTransaction {
        keyboxQueries.rotateAppGlobalAuthKeyHwSignature(
          id = keybox.localId,
          appGlobalAuthKeyHwSignature = signature
        )
      }.bind()

      // Re-read the keybox from DB to get the canonical updated state.
      // Check both active and onboarding accounts since this may run during onboarding.
      val account = db.fullAccountQueries
        .getActiveFullAccount()
        .awaitAsOneOrNullResult()
        .bind()
        ?: db.fullAccountQueries
          .getOnboardingFullAccount()
          .awaitAsOneOrNullResult()
          .bind()

      checkNotNull(account) { "No account found after signature update" }
      account.keybox().bind()
    }.logFailure { "Failed to update appGlobalAuthKeyHwSignature" }

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

  private suspend fun FullAccountView.keybox(): Result<Keybox, DbError> =
    coroutineBinding {
      val keysets = databaseProvider.database().spendingKeysetQueries.allKeysetsForKeybox(keyboxId)
        .awaitAsListResult()
        .bind()
        .map {
          SpendingKeyset(
            localId = it.id,
            f8eSpendingKeyset = it.serverKey,
            appKey = it.appKey,
            hardwareKey = it.hardwareKey,
            networkType = networkType
          )
        }

      Keybox(
        localId = keyboxId,
        fullAccountId = accountId,
        keysets = keysets,
        canUseKeyboxKeysets = canUseKeyboxKeysets,
        activeSpendingKeyset = SpendingKeyset(
          localId = spendingPublicKeysetId,
          f8eSpendingKeyset = serverKey,
          appKey = appKey,
          hardwareKey = hardwareKey,
          networkType = networkType
        ),
        activeAppKeyBundle = AppKeyBundle(
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
        config = FullAccountConfig(
          bitcoinNetworkType = networkType,
          isHardwareFake = fakeHardware,
          f8eEnvironment = f8eEnvironment,
          isTestAccount = isTestAccount,
          isUsingSocRecFakes = isUsingSocRecFakes,
          hardwareType = hardwareType,
          delayNotifyDuration = delayNotifyDuration
        )
      )
    }
}
