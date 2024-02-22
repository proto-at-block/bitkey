package build.wallet.onboarding

import build.wallet.db.DbError
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.CurrencyPreference
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState.Incomplete
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class OnboardingKeyboxStepStateDaoFake : OnboardingKeyboxStepStateDao {
  private var cloudBackupStateFlow = MutableStateFlow(Incomplete)
  private var notificationPreferencesStateFlow = MutableStateFlow(Incomplete)
  private var currencyPreferenceStateFlow = MutableStateFlow(Incomplete)

  override suspend fun setStateForStep(
    step: OnboardingKeyboxStep,
    state: OnboardingKeyboxStepState,
  ): Result<Unit, DbError> {
    when (step) {
      CloudBackup -> cloudBackupStateFlow.emit(state)
      NotificationPreferences -> notificationPreferencesStateFlow.emit(state)
      CurrencyPreference -> currencyPreferenceStateFlow.emit(state)
    }
    return Ok(Unit)
  }

  override fun stateForStep(step: OnboardingKeyboxStep): Flow<OnboardingKeyboxStepState> {
    return when (step) {
      CloudBackup -> cloudBackupStateFlow
      NotificationPreferences -> notificationPreferencesStateFlow
      CurrencyPreference -> currencyPreferenceStateFlow
    }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    cloudBackupStateFlow = MutableStateFlow(Incomplete)
    notificationPreferencesStateFlow = MutableStateFlow(Incomplete)
    currencyPreferenceStateFlow = MutableStateFlow(Incomplete)
    return Ok(Unit)
  }
}
