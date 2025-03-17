package build.wallet.coroutines.scopes

import kotlinx.coroutines.*

object CoroutineScopes {
  /**
   * A [CoroutineScope] that is tied to the lifecycle of the application.
   *
   * Mostly used as a convenient way to launch quick, fire and forget side effects - for example
   * event tracking and logging.
   *
   * The usage of this scope should be as minimal as possible. It's always a much better choice
   * to rely on structured concurrency or use state machine [LaunchedEffect]s / `rememberCoroutineScope`
   * for performing async work.
   *
   * The scope should be injected through constructor, do not reference it directly.
   * `ActivityComponentImpl` provides this scope.
   *
   * Uses [SupervisorJob] to ensure that parent jobs are not canceled when a child job fails.
   */
  val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.Default) + CoroutineName("AppScope")
}
