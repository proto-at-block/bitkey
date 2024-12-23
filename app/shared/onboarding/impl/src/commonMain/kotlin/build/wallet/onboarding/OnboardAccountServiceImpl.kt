package build.wallet.onboarding

import build.wallet.debug.DebugOptionsService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.onboarding.OnboardAccountStep.CloudBackup
import build.wallet.onboarding.OnboardAccountStep.NotificationPreferences
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class OnboardAccountServiceImpl(
  private val debugOptionsService: DebugOptionsService,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
  private val onboardingKeyboxSealedCsekDao: OnboardingKeyboxSealedCsekDao,
) : OnboardAccountService {
  override suspend fun pendingStep(): Result<OnboardAccountStep?, Throwable> =
    coroutineBinding {
      val debugOptions = debugOptionsService.options().first()
      val cloudBackupStepState =
        onboardingKeyboxStepStateDao.stateForStep(OnboardingKeyboxStep.CloudBackup).first()
      if (cloudBackupStepState == OnboardingKeyboxStepState.Incomplete && !debugOptions.skipCloudBackupOnboarding) {
        val sealedCsek = onboardingKeyboxSealedCsekDao.get().bind()
        return@coroutineBinding CloudBackup(sealedCsek)
      }

      val notificationsStepState =
        onboardingKeyboxStepStateDao.stateForStep(OnboardingKeyboxStep.NotificationPreferences)
          .first()
      if (notificationsStepState == OnboardingKeyboxStepState.Incomplete && !debugOptions.skipNotificationsOnboarding) {
        return@coroutineBinding NotificationPreferences
      }

      // No more onboarding steps to complete
      null
    }.logFailure { "Error loading pending onboarding state." }

  override suspend fun completeStep(step: OnboardAccountStep): Result<Unit, Throwable> {
    return when (step) {
      is CloudBackup -> completeCloudBackupStep()
      is NotificationPreferences -> completeNotificationPreferencesStep()
    }
  }

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
