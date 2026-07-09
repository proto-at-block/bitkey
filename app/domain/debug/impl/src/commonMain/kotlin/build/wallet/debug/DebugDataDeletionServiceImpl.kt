package build.wallet.debug

import bitkey.recovery.DescriptorBackupVerificationDao
import build.wallet.account.AccountService
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.account.Account
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.bitkey.account.appRecoveryAuthKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.debug.DebugDataDeletionTarget.ActiveAccountCloudBackup
import build.wallet.debug.DebugDataDeletionTarget.ActiveAppGlobalAuthKey
import build.wallet.debug.DebugDataDeletionTarget.ActiveAppRecoveryAuthKey
import build.wallet.debug.DebugDataDeletionTarget.ActiveAppSpendingKey
import build.wallet.debug.DebugDataDeletionTarget.AllCloudBackupStores
import build.wallet.debug.DebugDataDeletionTarget.AllLocalAppData
import build.wallet.debug.DebugDataDeletionTarget.AllLocalAppPrivateKeys
import build.wallet.debug.DebugDataDeletionTarget.CloudBackupActiveKeyset
import build.wallet.debug.DebugDataDeletionTarget.CloudBackupsInStore
import build.wallet.debug.DebugDataDeletionTarget.CorruptCloudBackup
import build.wallet.debug.DebugDataDeletionTarget.DescriptorBackupVerificationState
import build.wallet.debug.DebugDataDeletionTarget.LocalCsek
import build.wallet.debug.DebugDataDeletionTarget.OnboardingAppKey
import build.wallet.debug.DebugDataDeletionTarget.OnboardingKeyboxMaterial
import build.wallet.debug.DebugDataDeletionTarget.RelationshipsKeys
import build.wallet.debug.cloud.CloudBackupCorrupter
import build.wallet.debug.cloud.CloudBackupDeleter
import build.wallet.debug.cloud.CloudBackupKeysetDeleter
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDao
import build.wallet.onboarding.OnboardingKeyboxSealedCsekDao
import build.wallet.onboarding.OnboardingKeyboxSealedSsekDao
import build.wallet.onboarding.OnboardingKeyboxStepStateDao
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.relationships.RelationshipsKeysDao
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class DebugDataDeletionServiceImpl(
  private val appVariant: AppVariant,
  private val accountService: AccountService,
  private val appDataDeleter: AppDataDeleter,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val cloudBackupCorrupter: CloudBackupCorrupter,
  private val cloudBackupDeleter: CloudBackupDeleter,
  private val cloudBackupKeysetDeleter: CloudBackupKeysetDeleter,
  private val csekDao: CsekDao,
  private val descriptorBackupVerificationDao: DescriptorBackupVerificationDao,
  private val onboardingAppKeyKeystore: OnboardingAppKeyKeystore,
  private val onboardingKeyboxHardwareKeysDao: OnboardingKeyboxHardwareKeysDao,
  private val onboardingKeyboxSealedCsekDao: OnboardingKeyboxSealedCsekDao,
  private val onboardingKeyboxSealedSsekDao: OnboardingKeyboxSealedSsekDao,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
  private val relationshipsKeysDao: RelationshipsKeysDao,
) : DebugDataDeletionService {
  override suspend fun delete(targets: List<DebugDataDeletionTarget>): DebugDataDeletionReport {
    val deletedTargets = mutableListOf<DebugDataDeletionTarget>()
    val failures = mutableListOf<DebugDataDeletionFailure>()
    val activeAccountAtStart = activeAccountOrNull()

    targets.distinct().forEach { target ->
      if (appVariant == Customer) {
        failures += DebugDataDeletionFailure(
          target = target,
          message = "Not allowed to delete debug data in Customer builds."
        )
        return@forEach
      }

      deleteTarget(
        target = target,
        activeAccountAtStart = activeAccountAtStart
      )
        .mapError { error ->
          failures += DebugDataDeletionFailure(
            target = target,
            message = error.message ?: "Unknown error"
          )
        }
        .also { result ->
          if (result.isOk) {
            deletedTargets += target
          }
        }
    }

    return DebugDataDeletionReport(
      deletedTargets = deletedTargets,
      failures = failures
    )
  }

  private suspend fun deleteTarget(
    target: DebugDataDeletionTarget,
    activeAccountAtStart: Account?,
  ): Result<Unit, Error> =
    coroutineBinding {
      when (target) {
        ActiveAppSpendingKey -> {
          val account = activeFullAccount().bind()
          val key = account.keybox.activeAppKeyBundle.spendingKey
          appPrivateKeyDao.remove(key).mapError(::asError).bind()
        }
        ActiveAppGlobalAuthKey -> {
          val key = activeAppGlobalAuthKey().bind()
          appPrivateKeyDao.remove(key).mapError(::asError).bind()
        }
        ActiveAppRecoveryAuthKey -> {
          val account = activeAccount().bind()
          appPrivateKeyDao.remove(account.appRecoveryAuthKey).mapError(::asError).bind()
        }
        AllLocalAppPrivateKeys -> appPrivateKeyDao.clear().mapError(::asError).bind()
        OnboardingAppKey -> onboardingAppKeyKeystore.clear().mapError(::asError).bind()
        OnboardingKeyboxMaterial -> {
          onboardingKeyboxSealedCsekDao.clear().mapError(::asError).bind()
          onboardingKeyboxSealedSsekDao.clear().mapError(::asError).bind()
          onboardingKeyboxStepStateDao.clear().bind()
          onboardingKeyboxHardwareKeysDao.clear()
        }
        LocalCsek -> csekDao.clear().mapError(::asError).bind()
        RelationshipsKeys -> relationshipsKeysDao.clear().bind()
        DescriptorBackupVerificationState -> descriptorBackupVerificationDao.clear().bind()
        ActiveAccountCloudBackup -> {
          val accountId = activeAccountAtStart?.accountId
            ?: Err(Error("No active account.")).bind()
          cloudBackupDeleter.delete(accountId).bind()
        }
        AllCloudBackupStores -> cloudBackupDeleter.deleteAllBackups().bind()
        is CloudBackupsInStore -> cloudBackupDeleter.deleteBackupsIn(target.storeType).bind()
        AllLocalAppData -> appDataDeleter.deleteAll().bind()
        CorruptCloudBackup -> {
          val accountId = activeAccountAtStart?.accountId
            ?: Err(Error("No active account.")).bind()
          cloudBackupCorrupter.corrupt(accountId).mapError { Error(it.message, it) }.bind()
        }
        CloudBackupActiveKeyset ->
          cloudBackupKeysetDeleter.deleteActiveKeyset().mapError { Error(it.message, it) }.bind()
      }
    }

  private suspend fun activeFullAccount(): Result<FullAccount, Error> =
    coroutineBinding {
      when (val account = activeAccount().bind()) {
        is FullAccount -> account
        else -> Err(Error("No active full account with an app spending key.")).bind()
      }
    }

  private suspend fun activeAppGlobalAuthKey(): Result<PublicKey<AppGlobalAuthKey>, Error> =
    coroutineBinding {
      when (val account = activeAccount().bind()) {
        is FullAccount -> account.keybox.activeAppKeyBundle.authKey
        is SoftwareAccount -> account.keybox.authKey
        else -> Err(Error("No active account with an app global auth key.")).bind()
      }
    }

  private suspend fun activeAccount(): Result<Account, Error> =
    activeAccountOrNull()
      ?.let(::Ok)
      ?: Err(Error("No active account."))

  private suspend fun activeAccountOrNull(): Account? = accountService.activeAccount().first()

  private fun asError(throwable: Throwable): Error = Error(throwable.message, throwable)
}
