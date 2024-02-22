package build.wallet.statemachine.data.account.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.statemachine.data.account.OnboardConfig
import build.wallet.statemachine.data.account.OnboardConfigData
import kotlinx.coroutines.launch

class OnboardConfigDataStateMachineImpl(
  private val onboardingStepSkipConfigDao: OnboardingStepSkipConfigDao,
) : OnboardConfigDataStateMachine {
  @Composable
  override fun model(props: Unit): OnboardConfigData {
    val stepsToSkip = rememberStepsToSkip()
    val scope = rememberStableCoroutineScope()

    if (stepsToSkip == null) {
      return OnboardConfigData.LoadingOnboardConfigData
    }

    return OnboardConfigData.LoadedOnboardConfigData(
      config = OnboardConfig(stepsToSkip),
      setShouldSkipStep = { step, shouldSkip ->
        scope.launch {
          onboardingStepSkipConfigDao.setShouldSkipOnboardingStep(step, shouldSkip)
        }
      }
    )
  }

  @Composable
  private fun rememberStepsToSkip(): Set<OnboardingKeyboxStep>? {
    return remember {
      onboardingStepSkipConfigDao.stepsToSkip()
    }.collectAsState(null).value
  }
}
