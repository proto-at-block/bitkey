package build.wallet.onboarding

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
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

  override suspend fun getFallbackCompletion(): Result<Boolean, Error> {
    return onboardingCompletionDao.getCompletionTimestamp(
      id = onboardingCompletionDao.fallbackKeyId
    ).map { it != null }
  }

  override suspend fun recordFallbackCompletion(): Result<Unit, Error> {
    return onboardingCompletionDao.recordCompletion(
      id = onboardingCompletionDao.fallbackKeyId,
      timestamp = clock.now()
    )
  }
}
