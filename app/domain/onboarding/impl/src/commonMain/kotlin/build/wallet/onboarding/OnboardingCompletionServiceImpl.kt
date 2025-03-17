package build.wallet.onboarding

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@BitkeyInject(AppScope::class)
class OnboardingCompletionServiceImpl(
  private val onboardingCompletionDao: OnboardingCompletionDao,
  private val clock: Clock,
) : OnboardingCompletionService {
  override suspend fun recordCompletion(): Result<Unit, Error> {
    return onboardingCompletionDao.recordCompletion(timestamp = clock.now())
  }

  override suspend fun recordCompletionIfNotExists(): Result<Unit, Error> {
    return onboardingCompletionDao.recordCompletionIfNotExists(timestamp = clock.now())
  }

  override suspend fun getCompletionTimestamp(): Result<Instant?, Error> {
    return onboardingCompletionDao.getCompletionTimestamp()
  }

  override suspend fun clearOnboardingTimestamp(): Result<Unit, Error> {
    return onboardingCompletionDao.clearCompletionTimestamp()
  }
}
