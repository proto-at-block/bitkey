package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.ktor.result.EmptyResponseBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.*
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

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
      .bodyResult<PrivilegedActionInstancesResponse> {
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

  /**
   * Cancel a privileged action instance
   */
  suspend fun cancelPrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: CancelPrivilegedActionRequest,
  ): Result<EmptyResponseBody, Throwable> {
    return f8eHttpClient.authenticated()
      .bodyResult<EmptyResponseBody> {
        post("/api/privileged-actions/cancel") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setBody(request)
        }
      }
  }
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
  @SerialName("privileged_action_type")
  val privilegedActionType: PrivilegedActionType,
  @SerialName("authorization_strategy")
  val authorizationStrategy: AuthorizationStrategy,
) : RedactedResponseBody

@Serializable
data class PrivilegedActionInstancesResponse(
  @Unredacted
  @SerialName("instances")
  val privilegedActionInstances: List<PrivilegedActionInstance>,
) : RedactedResponseBody

@Serializable
data class PrivilegedActionInstanceResponse(
  @Unredacted
  @SerialName("privileged_action_instance")
  val privilegedActionInstance: PrivilegedActionInstance,
) : RedactedResponseBody

@Serializable
enum class AuthorizationStrategyType {
  DELAY_AND_NOTIFY,
  HARDWARE_PROOF_OF_POSSESSION,
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("authorization_strategy_type")
sealed class AuthorizationStrategy {
  @SerialName("authorization_strategy_type")
  abstract val authorizationStrategyType: AuthorizationStrategyType

  @Serializable
  @SerialName("DELAY_AND_NOTIFY")
  data class DelayAndNotify(
    @SerialName("authorization_strategy_type")
    override val authorizationStrategyType: AuthorizationStrategyType,
    @SerialName("delay_end_time")
    val delayEndTime: Instant,
    @SerialName("cancellation_token")
    val cancellationToken: String,
    @SerialName("completion_token")
    val completionToken: String,
  ) : AuthorizationStrategy()
}

/**
 * Request to cancel a privileged action
 */
@Serializable
data class CancelPrivilegedActionRequest(
  @SerialName("cancellation_token")
  val cancellationToken: String,
)
