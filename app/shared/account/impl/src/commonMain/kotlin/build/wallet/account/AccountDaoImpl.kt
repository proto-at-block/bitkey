package build.wallet.account

import app.cash.sqldelight.async.coroutines.awaitAsOne
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.*
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.SoftwareKeybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.*
import build.wallet.db.DbError
import build.wallet.frost.KeyCommitments
import build.wallet.frost.PublicKey
import build.wallet.frost.ShareDetails
import build.wallet.logging.logFailure
import build.wallet.mapResult
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class AccountDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : AccountDao {
  override fun activeAccount(): Flow<Result<Account?, DbError>> {
    return combine(
      activeFullAccount(),
      activeLiteAccount(),
      activeSoftwareAccount()
    ) { activeFullAccountResult, activeLiteAccountResult, activeSoftwareAccountResult ->
      coroutineBinding {
        val activeFullAccount = activeFullAccountResult.bind()
        val activeLiteAccount = activeLiteAccountResult.bind()
        val activeSoftwareAccount = activeSoftwareAccountResult.bind()
        activeFullAccount ?: activeSoftwareAccount ?: activeLiteAccount
      }
    }
  }

  override fun onboardingAccount(): Flow<Result<Account?, DbError>> {
    return combine(
      onboardingFullAccount(),
      onboardingLiteAccount(),
      onboardingSoftwareAccount()
    ) { onboardingFullAccountResult, onboardingLiteAccountResult, onboardingSoftwareAccountResult ->
      coroutineBinding {
        val onboardingFullAccount = onboardingFullAccountResult.bind()
        val onboardingLiteAccount = onboardingLiteAccountResult.bind()
        val onboardingSoftwareAccount = onboardingSoftwareAccountResult.bind()
        onboardingFullAccount ?: onboardingSoftwareAccount ?: onboardingLiteAccount
      }
    }
  }

  override suspend fun setActiveAccount(account: Account): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      when (account) {
        // TODO(BKR-488): manage active Full Accounts using Account entity.
        is FullAccount -> error("not implemented")
        is LiteAccount -> {
          liteAccountQueries.setActiveLiteAccountId(accountId = account.accountId)
          liteAccountQueries.clearOnboardingLiteAccount()
        }
        is SoftwareAccount -> {
          softwareAccountQueries.activateSoftwareAccountAfterOnboarding(
            softwareKeyboxId = account.keybox.id,
            accountId = account.accountId,
            appGlobalAuthKey = account.keybox.authKey,
            appRecoveryAuthKey = account.keybox.recoveryAuthKey
          )
        }
        // TODO (W-8720): An OnboardingSoftwareAccount should never be set as active.
        // This is a downside to our current separation of Onboarding and
        // ActiveSoftwareAccount. We'll need to reconsider our interfaces for these two account states.
        is OnboardingSoftwareAccount -> error("Can't set an onboarding account as active")
      }
    }.logFailure { "Error setting active account : $account" }
  }

  override suspend fun saveAccountAndBeginOnboarding(account: Account): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      when (account) {
        // TODO(BKR-488): manage active Full Accounts using Account entity.
        is FullAccount -> error("not implemented")
        is LiteAccount -> {
          liteAccountQueries.insertLiteAccount(liteAccount = account)
          liteAccountQueries.setOnboardingLiteAccountId(accountId = account.accountId)
        }
        is OnboardingSoftwareAccount -> {
          softwareAccountQueries.setOnboardingSoftwareAccount(
            accountId = account.accountId,
            appGlobalAuthKey = account.appGlobalAuthKey,
            appRecoveryAuthKey = account.recoveryAuthKey,
            bitcoinNetworkType = account.config.bitcoinNetworkType,
            f8eEnvironment = account.config.f8eEnvironment,
            isTestAccount = account.config.isTestAccount,
            isUsingSocRecFakes = account.config.isUsingSocRecFakes
          )
        }
        is SoftwareAccount -> error("cannot set active software account for onboarding")
      }
    }.logFailure { "Failed to save account" }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      liteAccountQueries.clear()
      fullAccountQueries.clear()
      softwareAccountQueries.clear()
      // TODO (W-9113): Clean up inconsistency between AccountDao & KeyboxDao
      softwareKeyboxQueries.clear()
    }.logFailure { "Error clearing account database state." }
  }

  private fun activeLiteAccount(): Flow<Result<LiteAccount?, DbError>> {
    return flow {
      databaseProvider.database().liteAccountQueries
        .getActiveLiteAccount()
        .asFlowOfOneOrNull()
        .mapResult { it?.toLiteAccount() }
        .collect(::emit)
    }
  }

  private fun activeFullAccount(): Flow<Result<FullAccount?, DbError>> {
    return flow {
      val database = databaseProvider.database()
      database.fullAccountQueries
        .getActiveFullAccount()
        .asFlowOfOneOrNull()
        .mapResult { it?.toFullAccount(database) }
        .collect(::emit)
    }
  }

  private fun activeSoftwareAccount(): Flow<Result<SoftwareAccount?, DbError>> {
    return flow {
      databaseProvider.database()
        .softwareAccountQueries
        .getActiveSoftwareAccount()
        .asFlowOfOneOrNull()
        .mapResult { it?.toSoftwareAccount() }
        .collect(::emit)
    }
  }

  private fun onboardingLiteAccount(): Flow<Result<LiteAccount?, DbError>> {
    return flow {
      databaseProvider.database()
        .liteAccountQueries
        .getOnboardingLiteAccount()
        .asFlowOfOneOrNull()
        .mapResult { it?.toLiteAccount() }
        .collect(::emit)
    }
  }

  private fun onboardingFullAccount(): Flow<Result<FullAccount?, DbError>> {
    return flow {
      val database = databaseProvider.database()
      database.fullAccountQueries
        .getOnboardingFullAccount()
        .asFlowOfOneOrNull()
        .mapResult { it?.toFullAccount(database) }
        .collect(::emit)
    }
  }

  private fun onboardingSoftwareAccount(): Flow<Result<OnboardingSoftwareAccount?, DbError>> {
    return flow {
      databaseProvider.database()
        .softwareAccountQueries
        .getOnboardingSoftwareAccount()
        .asFlowOfOneOrNull()
        .mapResult { it?.toOnboardingSoftwareAccount() }
        .collect(::emit)
    }
  }
}

private fun GetOnboardingSoftwareAccount.toOnboardingSoftwareAccount() =
  OnboardingSoftwareAccount(
    accountId = accountId,
    config = SoftwareAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes
    ),
    appGlobalAuthKey = appGlobalAuthKey,
    recoveryAuthKey = appRecoveryAuthKey
  )

private fun SoftwareAccountWithKeyboxView.toSoftwareAccount() =
  SoftwareAccount(
    accountId = accountId,
    config = SoftwareAccountConfig(
      bitcoinNetworkType = bitcoinNetworkType,
      f8eEnvironment = f8eEnvironment,
      isTestAccount = isTestAccount,
      isUsingSocRecFakes = isUsingSocRecFakes
    ),
    keybox = SoftwareKeybox(
      id = keyboxId,
      networkType = bitcoinNetworkType,
      authKey = appGlobalAuthKey,
      recoveryAuthKey = appRecoveryAuthKey,
      // TODO [W-10001]: Persist software account
      shareDetails = object : ShareDetails {
        override val secretShare: List<UByte>
          get() = emptyList()
        override val keyCommitments: KeyCommitments
          get() = object : KeyCommitments {
            override val vssCommitments: List<PublicKey>
              get() = emptyList()
            override val aggregatePublicKey: PublicKey
              get() = object : PublicKey {
                override fun asString(): String {
                  return ""
                }
              }
          }
      }
    )
  )

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
