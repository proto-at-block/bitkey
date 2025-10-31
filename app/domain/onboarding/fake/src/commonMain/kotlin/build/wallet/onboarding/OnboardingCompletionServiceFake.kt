package build.wallet.onboarding

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

class OnboardingCompletionServiceFake : OnboardingCompletionService {
  private var completionTimestamp: Instant? = null
  private var fallbackCompletion: Boolean = false

  var recordCompletionResult: Result<Unit, Error> = Ok(Unit)
  var recordCompletionIfNotExistsResult: Result<Unit, Error> = Ok(Unit)
  var getCompletionTimestampResult: Result<Instant?, Error> = Ok(null)
  var clearOnboardingTimestampResult: Result<Unit, Error> = Ok(Unit)
  var getFallbackCompletionResult: Result<Boolean, Error> = Ok(false)
  var recordFallbackCompletionResult: Result<Unit, Error> = Ok(Unit)

  var recordFallbackCompletionCalled: Boolean = false

  override suspend fun recordCompletion(): Result<Unit, Error> {
    return recordCompletionResult
  }

  override suspend fun recordCompletionIfNotExists(): Result<Unit, Error> {
    return recordCompletionIfNotExistsResult
  }

  override suspend fun getCompletionTimestamp(): Result<Instant?, Error> {
    return getCompletionTimestampResult.also {
      if (it.isOk) {
        getCompletionTimestampResult = Ok(completionTimestamp)
      }
    }
  }

  override suspend fun clearOnboardingTimestamp(): Result<Unit, Error> {
    completionTimestamp = null
    return clearOnboardingTimestampResult
  }

  override suspend fun getFallbackCompletion(): Result<Boolean, Error> {
    return getFallbackCompletionResult.also {
      if (it.isOk) {
        getFallbackCompletionResult = Ok(fallbackCompletion)
      }
    }
  }

  override suspend fun recordFallbackCompletion(): Result<Unit, Error> {
    recordFallbackCompletionCalled = true
    fallbackCompletion = true
    return recordFallbackCompletionResult
  }

  fun reset() {
    completionTimestamp = null
    fallbackCompletion = false
    recordCompletionResult = Ok(Unit)
    recordCompletionIfNotExistsResult = Ok(Unit)
    getCompletionTimestampResult = Ok(null)
    clearOnboardingTimestampResult = Ok(Unit)
    getFallbackCompletionResult = Ok(false)
    recordFallbackCompletionResult = Ok(Unit)
    recordFallbackCompletionCalled = false
  }
}
