package bitkey.privilegedactions

import kotlinx.datetime.Instant

sealed interface AuthorizationStrategy {
  val authorizationStrategyType: AuthorizationStrategyType

  data class DelayAndNotify(
    override val authorizationStrategyType: AuthorizationStrategyType,
    val delayStartTime: Instant,
    val delayEndTime: Instant,
    val cancellationToken: String,
    val completionToken: String,
  ) : AuthorizationStrategy

  data class OutOfBand(
    override val authorizationStrategyType: AuthorizationStrategyType,
  ) : AuthorizationStrategy
}
