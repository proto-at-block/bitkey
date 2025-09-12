package build.wallet.onboarding

import build.wallet.account.AccountService
import build.wallet.account.getAccountOrNull
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.onboarding.OnboardingF8eClient
import build.wallet.feature.flags.OnboardingCompletionFailsafeFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logInfo
import build.wallet.worker.RetryStrategy
import build.wallet.worker.RunStrategy
import build.wallet.worker.TimeoutStrategy
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class OnboardingCompletionFailsafeWorkerImpl(
  private val accountService: AccountService,
  private val onboardingCompletionService: OnboardingCompletionService,
  private val onboardingCompletionFailsafeFeatureFlag: OnboardingCompletionFailsafeFeatureFlag,
  private val onboardingF8eClient: OnboardingF8eClient,
) : OnboardingCompletionFailsafeWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(),
    RunStrategy.OnEvent(onboardingCompletionFailsafeFeatureFlag.flagValue())
  )

  override val retryStrategy: RetryStrategy = RetryStrategy.Always(
    delay = 10.seconds,
    retries = 2
  )

  override val timeout: TimeoutStrategy = TimeoutStrategy.Always(30.seconds)

  override suspend fun executeWork() {
    if (!onboardingCompletionFailsafeFeatureFlag.isEnabled()) return

    val account = accountService.getAccountOrNull<FullAccount>().get()
    val onboardingIsComplete = onboardingCompletionService.getFallbackCompletion().get() ?: false
    // Condition we're checking
    // - user did not go through onboarding happy path, so OnboardFullAccountService#activateAccount
    // was never called. This can happen if you complete your cloud backup, delete the app before
    // completing the rest of onboarding (e.g. touchpoints), then recover via the cloud backup
    // We don't actually know if we went through the happy path, but we can call completeOnboarding
    // anyway since it is idempotent -- repeat calls have no effect
    // - Device does not have an onboarding completion timestamp saved to its local database.
    if (account != null && !onboardingIsComplete) {
      logInfo(tag = "onboarding_completion_failsafe") {
        "Calling completeOnboarding() from failsafe worker"
      }
      onboardingF8eClient.completeOnboarding(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      ).onSuccess {
        onboardingCompletionService.recordFallbackCompletion()
      }.onFailure { error ->
        // Throw error to ensure retry occurs in AppWorkerExecutorImpl
        throw error
      }
    }
  }
}
