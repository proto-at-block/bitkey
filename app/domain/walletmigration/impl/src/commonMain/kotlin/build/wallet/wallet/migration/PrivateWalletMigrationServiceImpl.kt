package build.wallet.wallet.migration

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClient
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class PrivateWalletMigrationServiceImpl(
  private val keyGenerator: AppKeysGenerator,
  private val createKeysetClient: CreateAccountKeysetV2F8eClient,
  private val uuidGenerator: UuidGenerator,
) : PrivateWalletMigrationService {
  override suspend fun initiateMigration(
    account: FullAccount,
    proofOfPossession: HwFactorProofOfPossession,
    newHwKeys: HwKeyBundle,
  ): Result<SpendingKeyset, PrivateWalletMigrationError> {
    return coroutineBinding {
      val newAppKeys = keyGenerator.generateKeyBundle()
        .mapError { PrivateWalletMigrationError.KeysetCreationFailed(it) }
        .bind()
      val newServerKeys = createKeysetClient.createKeyset(
        f8eEnvironment = account.keybox.config.f8eEnvironment,
        fullAccountId = account.accountId,
        hardwareSpendingKey = newHwKeys.spendingKey,
        appSpendingKey = newAppKeys.spendingKey,
        network = newAppKeys.networkType,
        appAuthKey = newAppKeys.authKey,
        hardwareProofOfPossession = proofOfPossession
      ).mapError { PrivateWalletMigrationError.KeysetCreationFailed(it) }
        .bind()

      SpendingKeyset(
        localId = uuidGenerator.random(),
        networkType = account.keybox.config.bitcoinNetworkType,
        appKey = newAppKeys.spendingKey,
        hardwareKey = newHwKeys.spendingKey,
        f8eSpendingKeyset = newServerKeys
      )
    }
  }

  override suspend fun prepareSweep(
    account: FullAccount,
  ): Result<PrivateWalletSweep?, PrivateWalletMigrationError> {
    return Err(PrivateWalletMigrationError.FeatureNotAvailable)
  }

  override suspend fun finalizeMigration(
    sweepTxid: String,
    account: FullAccount,
  ): Result<Keybox, PrivateWalletMigrationError> {
    return Err(PrivateWalletMigrationError.FeatureNotAvailable)
  }

  override suspend fun cancelMigration(
    account: FullAccount,
  ): Result<Unit, PrivateWalletMigrationError> {
    return Err(PrivateWalletMigrationError.FeatureNotAvailable)
  }
}
