package build.wallet.statemachine.data.account.create

import build.wallet.statemachine.data.account.OnboardConfig
import build.wallet.statemachine.data.account.OnboardConfigData

val LoadedOnboardConfigDataMock =
  OnboardConfigData.LoadedOnboardConfigData(
    config = OnboardConfig(stepsToSkip = emptySet()),
    setShouldSkipStep = { _, _ -> }
  )
