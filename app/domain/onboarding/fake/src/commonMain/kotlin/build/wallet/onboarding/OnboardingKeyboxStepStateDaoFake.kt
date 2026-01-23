package build.wallet.onboarding

import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.DescriptorBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState.Incomplete
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class OnboardingKeyboxStepStateDaoFake : OnboardingKeyboxStepStateDao {
  private var descriptorBackupStateFlow = MutableStateFlow(Incomplete)
  private var cloudBackupStateFlow = MutableStateFlow(Incomplete)
  private var notificationPreferencesStateFlow = MutableStateFlow(Incomplete)

  override suspend fun setStateForStep(
    step: OnboardingKeyboxStep,
    state: OnboardingKeyboxStepState,
  ): Result<Unit, Error> {
    when (step) {
      DescriptorBackup -> descriptorBackupStateFlow.emit(state)
      CloudBackup -> cloudBackupStateFlow.emit(state)
      NotificationPreferences -> notificationPreferencesStateFlow.emit(state)
    }
    return Ok(Unit)
  }

  override fun stateForStep(step: OnboardingKeyboxStep): Flow<OnboardingKeyboxStepState> {
    return when (step) {
      DescriptorBackup -> descriptorBackupStateFlow
      CloudBackup -> cloudBackupStateFlow
      NotificationPreferences -> notificationPreferencesStateFlow
    }
  }

  override suspend fun clear(): Result<Unit, Error> {
    descriptorBackupStateFlow.emit(Incomplete)
    cloudBackupStateFlow.emit(Incomplete)
    notificationPreferencesStateFlow.emit(Incomplete)
    return Ok(Unit)
  }
}
