package build.wallet.statemachine.data.account.create

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

class OnboardingStepSkipConfigDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : OnboardingStepSkipConfigDao {
  private val database by lazy { databaseProvider.debugDatabase() }

  override fun stepsToSkip(): Flow<Set<OnboardingKeyboxStep>> {
    return database.onboardingStepSkipConfigQueries.getSkippedSteps()
      .asFlowOfList()
      .mapLatest { result ->
        result
          .getOrElse { emptyList() }
          .map { it.onboardingStep }
          .toSet()
      }
  }

  override suspend fun setShouldSkipOnboardingStep(
    step: OnboardingKeyboxStep,
    shouldSkip: Boolean,
  ) {
    database.onboardingStepSkipConfigQueries.awaitTransaction {
      updateShouldSkipStep(step, shouldSkip)
    }
  }
}
