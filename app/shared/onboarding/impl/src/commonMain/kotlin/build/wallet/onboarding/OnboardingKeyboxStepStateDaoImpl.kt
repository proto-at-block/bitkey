package build.wallet.onboarding

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.onboarding.OnboardingKeyboxStepState.Incomplete
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class OnboardingKeyboxStepStateDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : OnboardingKeyboxStepStateDao {
  override suspend fun setStateForStep(
    step: OnboardingKeyboxStep,
    state: OnboardingKeyboxStepState,
  ): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      onboardingKeyboxStepStateQueries.setStateForStep(step, state)
    }.logFailure { "Failed to set onboarding step state" }
  }

  override fun stateForStep(step: OnboardingKeyboxStep): Flow<OnboardingKeyboxStepState> {
    return flow {
      databaseProvider.database()
        .onboardingKeyboxStepStateQueries
        .getStateForStep(step)
        .asFlowOfOneOrNull()
        .map { it.component1()?.state ?: Incomplete }
        .collect(::emit)
    }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database().awaitTransaction {
      onboardingKeyboxStepStateQueries.clear()
    }.logFailure { "Failed to clear onboarding step states" }
  }
}
