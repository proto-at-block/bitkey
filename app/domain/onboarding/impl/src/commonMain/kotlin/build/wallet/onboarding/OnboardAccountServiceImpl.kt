package build.wallet.onboarding

import bitkey.account.AccountConfigService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.EncryptedDescriptorBackupsFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logFailure
import build.wallet.onboarding.OnboardAccountStep.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class OnboardAccountServiceImpl(
  private val accountConfigService: AccountConfigService,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
  private val onboardingKeyboxSealedCsekDao: OnboardingKeyboxSealedCsekDao,
  private val onboardingKeyboxSealedSsekDao: OnboardingKeyboxSealedSsekDao,
  private val onboardingCompletionService: OnboardingCompletionService,
  private val encryptedDescriptorBackupsFeatureFlag: EncryptedDescriptorBackupsFeatureFlag,
) : OnboardAccountService {
  override suspend fun pendingStep(): Result<OnboardAccountStep?, Throwable> =
    coroutineBinding {
      val defaultConfig = accountConfigService.defaultConfig().first()

      // If enabled, descriptor backups MUST happen before cloud backups.
      if (encryptedDescriptorBackupsFeatureFlag.isEnabled()) {
        val descriptorStepState =
          onboardingKeyboxStepStateDao.stateForStep(OnboardingKeyboxStep.DescriptorBackup).first()

        if (descriptorStepState == OnboardingKeyboxStepState.Incomplete) {
          val sealedSsek = onboardingKeyboxSealedSsekDao.get().bind()
          return@coroutineBinding DescriptorBackup(
            sealedSsek = sealedSsek
          )
        }
      }

      val cloudBackupStepState =
        onboardingKeyboxStepStateDao.stateForStep(OnboardingKeyboxStep.CloudBackup).first()
      if (cloudBackupStepState == OnboardingKeyboxStepState.Incomplete && !defaultConfig.skipCloudBackupOnboarding) {
        val sealedCsek = onboardingKeyboxSealedCsekDao.get().bind()
        return@coroutineBinding CloudBackup(sealedCsek)
      }

      val notificationsStepState =
        onboardingKeyboxStepStateDao.stateForStep(OnboardingKeyboxStep.NotificationPreferences)
          .first()
      if (notificationsStepState == OnboardingKeyboxStepState.Incomplete && !defaultConfig.skipNotificationsOnboarding) {
        return@coroutineBinding NotificationPreferences
      }

      onboardingCompletionService.recordCompletion()

      // No more onboarding steps to complete
      null
    }.logFailure { "Error loading pending onboarding state." }

  override suspend fun completeStep(step: OnboardAccountStep): Result<Unit, Throwable> {
    return when (step) {
      is DescriptorBackup -> completeDescriptorBackupStep()
      is CloudBackup -> completeCloudBackupStep()
      is NotificationPreferences -> completeNotificationPreferencesStep()
    }
  }

  override suspend fun markStepIncomplete(step: OnboardAccountStep): Result<Unit, Throwable> {
    return coroutineBinding {
      val step = when (step) {
        is DescriptorBackup -> OnboardingKeyboxStep.DescriptorBackup
        is CloudBackup -> OnboardingKeyboxStep.CloudBackup
        is NotificationPreferences -> OnboardingKeyboxStep.NotificationPreferences
      }

      onboardingKeyboxStepStateDao
        .setStateForStep(
          step = step,
          state = OnboardingKeyboxStepState.Incomplete
        )
        .bind()
    }
  }

  private suspend fun completeDescriptorBackupStep(): Result<Unit, Throwable> =
    coroutineBinding {
      onboardingKeyboxStepStateDao
        .setStateForStep(
          step = OnboardingKeyboxStep.DescriptorBackup,
          state = OnboardingKeyboxStepState.Complete
        )
        .bind()
    }.logFailure { "Error completing descriptor backup onboarding step." }

  private suspend fun completeCloudBackupStep(): Result<Unit, Throwable> =
    coroutineBinding {
      onboardingKeyboxStepStateDao
        .setStateForStep(
          step = OnboardingKeyboxStep.CloudBackup,
          state = OnboardingKeyboxStepState.Complete
        )
        .bind()

      // Now that the backup is complete, we don't need the sealed csek anymore
      onboardingKeyboxSealedCsekDao.clear().bind()
      // Yes, I know you're wondering why we don't clear this in completeDescriptorBackupStep. Well,
      // the reason is we perform implicit lite account -> full account upgrades during cloud backup
      // if we discover an existing lite account in your cloud. That means the descriptor backup
      // step may be run for an account that isn't the final one. Yes this sucks. But either way,
      // keeping the sealed ssek around lets us go back to the descriptor backup step in this scenario
      // without requiring another hardware tap. And it'll get cleared here eventually anyway.
      onboardingKeyboxSealedSsekDao.clear().bind()
    }.logFailure { "Error completing cloud backup onboarding step." }

  private suspend fun completeNotificationPreferencesStep(): Result<Unit, Throwable> {
    return onboardingKeyboxStepStateDao
      .setStateForStep(
        OnboardingKeyboxStep.NotificationPreferences,
        OnboardingKeyboxStepState.Complete
      )
      .logFailure { "Error completing notification preferences onboarding step." }
  }
}
