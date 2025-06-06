package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Handles privileged actions for F8E.
 */
interface PrivilegedActionsF8eClient<Req, Res> {
  val f8eHttpClient: F8eHttpClient

  /**
   * Get all privileged action instances for an account
   */
  suspend fun getPrivilegedActionInstances(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<List<PrivilegedActionInstance>, Throwable> {
    return f8eHttpClient.authenticated()
      .bodyResult<PrivilegedActionInstancesResponseBody> {
        get("/api/accounts/${fullAccountId.serverId}/privileged-actions/instances") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
        }
      }.map { body -> body.privilegedActionInstances }
  }

  /**
   * Create a privileged action instance
   */
  suspend fun createPrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: Req,
  ): Result<PrivilegedActionInstance, Throwable>

  /**
   * Continue a privileged action instance after it's authorized
   */
  suspend fun continuePrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: ContinuePrivilegedActionRequest,
  ): Result<Res, Throwable>
}

/**
 * Request to continue a privileged action
 */
data class ContinuePrivilegedActionRequest(
  val privilegedActionInstance: PrivilegedActionInstanceRef,
)

/**
 * Reference to a privileged action instance with authorization details
 */
data class PrivilegedActionInstanceRef(
  val id: String,
  val authorizationStrategy: Authorization,
)

/**
 * Authorization with completion token
 */
data class Authorization(
  val authorizationStrategyType: AuthorizationStrategyType,
  val completionToken: String,
)

@Serializable
enum class PrivilegedActionType {
  RESET_FINGERPRINT,
}

@Serializable
data class PrivilegedActionInstance(
  val id: String,
  val privilegedActionType: PrivilegedActionType,
  val authorizationStrategy: AuthorizationStrategy,
) : RedactedResponseBody

@Serializable
data class PrivilegedActionInstancesResponseBody(
  @Unredacted
  val privilegedActionInstances: List<PrivilegedActionInstance>,
) : RedactedResponseBody

@Serializable
enum class AuthorizationStrategyType {
  DELAY_AND_NOTIFY,
  HARDWARE_PROOF_OF_POSSESSION,
}

@Serializable
sealed class AuthorizationStrategy {
  abstract val authorizationStrategyType: AuthorizationStrategyType

  data class DelayAndNotify(
    override val authorizationStrategyType: AuthorizationStrategyType,
    val delayEndTime: Instant,
    val cancellationToken: String? = null,
    val completionToken: String? = null,
  ) : AuthorizationStrategy()
}
