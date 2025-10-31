package build.wallet.onboarding

import app.cash.turbine.Turbine
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.DescriptorBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState.Incomplete
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class OnboardingKeyboxStepStateDaoMock(
  turbine: (name: String) -> Turbine<Any>,
) : OnboardingKeyboxStepStateDao {
  val clearCalls = turbine("clear calls")
  val setStateForStepCalls = turbine("set state for step calls")

  override suspend fun setStateForStep(
    step: OnboardingKeyboxStep,
    state: OnboardingKeyboxStepState,
  ): Result<Unit, Error> {
    val pair = Pair(step, state)
    setStateForStepCalls.add(pair)
    return Ok(Unit)
  }

  val descriptorBackupStateFlow = MutableStateFlow(Incomplete)
  val cloudBackupStateFlow = MutableStateFlow(Incomplete)
  val notificationPreferencesStateFlow = MutableStateFlow(Incomplete)

  override fun stateForStep(step: OnboardingKeyboxStep): Flow<OnboardingKeyboxStepState> {
    return when (step) {
      DescriptorBackup -> descriptorBackupStateFlow
      CloudBackup -> cloudBackupStateFlow
      NotificationPreferences -> notificationPreferencesStateFlow
    }
  }

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls.add(Unit)
    return Ok(Unit)
  }

  fun reset() {
    descriptorBackupStateFlow.value = Incomplete
    cloudBackupStateFlow.value = Incomplete
    notificationPreferencesStateFlow.value = Incomplete
  }
}
