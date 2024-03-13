package build.wallet.account

import app.cash.sqldelight.async.coroutines.awaitAsOne
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.database.sqldelight.FullAccountView
import build.wallet.database.sqldelight.GetActiveLiteAccount
import build.wallet.database.sqldelight.GetOnboardingLiteAccount
import build.wallet.database.sqldelight.LiteAccountQueries
import build.wallet.database.sqldelight.SpendingKeysetEntity
import build.wallet.db.DbError
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.mapResult
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import build.wallet.unwrapLoadedValue
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AccountDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : AccountDao {
  private val database by lazy { databaseProvider.database() }

  override fun activeAccount(): Flow<Result<Account?, DbError>> {
    return combine(
      activeFullAccount(),
      activeLiteAccount()
    ) { activeFullAccountResult, activeLiteAccountResult ->
      binding {
        val activeFullAccount = activeFullAccountResult.bind()
        val activeLiteAccount = activeLiteAccountResult.bind()
        activeFullAccount ?: activeLiteAccount
      }
    }
  }

  override fun onboardingAccount(): Flow<Result<Account?, DbError>> {
    return combine(
      onboardingFullAccount(),
      onboardingLiteAccount()
    ) { onboardingFullAccountResult, onboardingLiteAccountResult ->
      binding {
        val onboardingFullAccount = onboardingFullAccountResult.bind()
        val onboardingLiteAccount = onboardingLiteAccountResult.bind()
        onboardingFullAccount ?: onboardingLiteAccount
      }
    }
  }

  override suspend fun setActiveAccount(account: Account): Result<Unit, DbError> {
    return database.awaitTransaction {
      when (account) {
        // TODO(BKR-488): manage active Full Accounts using Account entity.
        is FullAccount -> error("not implemented")
        is LiteAccount -> {
          liteAccountQueries.setActiveLiteAccountId(accountId = account.accountId)
          liteAccountQueries.clearOnboardingLiteAccount()
        }
      }
    }.logFailure { "Error setting active account : $account" }
  }

  override suspend fun saveAccountAndBeginOnboarding(account: Account): Result<Unit, DbError> {
    log { "Saving account to local db" }
    return database.awaitTransaction {
      when (account) {
        // TODO(BKR-488): manage active Full Accounts using Account entity.
        is FullAccount -> error("not implemented")

        is LiteAccount -> {
          liteAccountQueries.insertLiteAccount(liteAccount = account)
          liteAccountQueries.setOnboardingLiteAccountId(accountId = account.accountId)
        }
      }
    }.logFailure { "Failed to save account" }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return database.awaitTransaction {
      liteAccountQueries.clear()
      fullAccountQueries.clear()
    }.logFailure { "Error clearing account database state." }
  }

  private fun activeLiteAccount(): Flow<Result<LiteAccount?, DbError>> {
    return database.liteAccountQueries
      .getActiveLiteAccount()
      .asFlowOfOneOrNull().unwrapLoadedValue()
      .mapResult { it?.toLiteAccount() }
  }

  private fun activeFullAccount(): Flow<Result<FullAccount?, DbError>> {
    return database.fullAccountQueries
      .getActiveFullAccount()
      .asFlowOfOneOrNull()
      .unwrapLoadedValue()
      .mapResult { it?.toFullAccount(database) }
  }

  private fun onboardingLiteAccount(): Flow<Result<LiteAccount?, DbError>> {
    return database.liteAccountQueries
      .getOnboardingLiteAccount()
      .asFlowOfOneOrNull().unwrapLoadedValue()
      .mapResult { it?.toLiteAccount() }
  }

  private fun onboardingFullAccount(): Flow<Result<FullAccount?, DbError>> {
    return database.fullAccountQueries
      .getOnboardingFullAccount()
      .asFlowOfOneOrNull()
      .unwrapLoadedValue()
      .mapResult { it?.toFullAccount(database) }
  }
}

private fun GetActiveLiteAccount.toLiteAccount() =
  LiteAccount(
    accountId = accountId,
    config =
      LiteAccountConfig(
        bitcoinNetworkType = bitcoinNetworkType,
        f8eEnvironment = f8eEnvironment,
        isTestAccount = isTestAccount,
        isUsingSocRecFakes = isUsingSocRecFakes
      ),
    recoveryAuthKey = appRecoveryAuthKey
  )

private fun GetOnboardingLiteAccount.toLiteAccount() =
  LiteAccount(
    accountId = accountId,
    config =
      LiteAccountConfig(
        bitcoinNetworkType = bitcoinNetworkType,
        f8eEnvironment = f8eEnvironment,
        isTestAccount = isTestAccount,
        isUsingSocRecFakes = isUsingSocRecFakes
      ),
    recoveryAuthKey = appRecoveryAuthKey
  )

private suspend fun FullAccountView.toFullAccount(database: BitkeyDatabase): FullAccount {
  val keybox = keybox(database)
  return FullAccount(
    accountId = accountId,
    config = keybox.config,
    keybox = keybox
  )
}

private suspend fun FullAccountView.keybox(database: BitkeyDatabase): Keybox {
  // Get the inactive keysets
  val inactiveKeysets =
    inactiveKeysetIds.map {
      val spendingPublicKeysetView = database.spendingKeysetQueries.keysetById(it).awaitAsOne()
      spendingPublicKeysetView.spendingKeyset(networkType)
    }

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
    inactiveKeysets = inactiveKeysets.toImmutableList(),
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

private fun SpendingKeysetEntity.spendingKeyset(networkType: BitcoinNetworkType): SpendingKeyset =
  SpendingKeyset(
    localId = id,
    f8eSpendingKeyset =
      F8eSpendingKeyset(
        keysetId = serverId,
        spendingPublicKey = serverKey
      ),
    appKey = appKey,
    hardwareKey = hardwareKey,
    networkType = networkType
  )

private fun LiteAccountQueries.insertLiteAccount(liteAccount: LiteAccount) =
  insertLiteAccount(
    accountId = liteAccount.accountId,
    bitcoinNetworkType = liteAccount.config.bitcoinNetworkType,
    isTestAccount = liteAccount.config.isTestAccount,
    f8eEnvironment = liteAccount.config.f8eEnvironment,
    isUsingSocRecFakes = liteAccount.config.isUsingSocRecFakes,
    appRecoveryAuthKey = liteAccount.recoveryAuthKey
  )
