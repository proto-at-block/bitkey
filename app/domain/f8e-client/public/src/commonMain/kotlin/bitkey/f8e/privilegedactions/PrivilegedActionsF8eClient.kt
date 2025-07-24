package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.ktor.result.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.get
import io.ktor.client.request.post
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
          setRedactedBody(request)
        }
      }
  }
}

/**
 * Request to continue a privileged action
 */
@Serializable
data class ContinuePrivilegedActionRequest(
  @SerialName("privileged_action_instance")
  val privilegedActionInstance: PrivilegedActionInstanceRef,
) : RedactedRequestBody

/**
 * Reference to a privileged action instance with authorization details
 */
@Serializable
data class PrivilegedActionInstanceRef(
  val id: String,
  @SerialName("authorization_strategy")
  val authorizationStrategy: Authorization,
)

/**
 * Authorization information with strategy type and completion token
 */
@Serializable
data class Authorization(
  @SerialName("authorization_strategy_type")
  val authorizationStrategyType: AuthorizationStrategyType,
  @SerialName("completion_token")
  val completionToken: String,
)

/**
 * Types of privileged actions that can be performed
 */
@Serializable
enum class PrivilegedActionType {
  RESET_FINGERPRINT,
}

/**
 * Represents a privileged action instance with its details
 */
@Serializable
data class PrivilegedActionInstance(
  val id: String,
  @SerialName("privileged_action_type")
  val privilegedActionType: PrivilegedActionType,
  @SerialName("authorization_strategy")
  val authorizationStrategy: AuthorizationStrategy,
) : RedactedResponseBody

/**
 * Response containing a list of privileged action instances
 */
@Serializable
data class PrivilegedActionInstancesResponse(
  @Unredacted
  @SerialName("instances")
  val privilegedActionInstances: List<PrivilegedActionInstance>,
) : RedactedResponseBody

/**
 * Response containing a single privileged action instance
 */
@Serializable
data class PrivilegedActionInstanceResponse(
  @Unredacted
  @SerialName("privileged_action_instance")
  val privilegedActionInstance: PrivilegedActionInstance,
) : RedactedResponseBody

/**
 * Types of authorization strategies for privileged actions
 */
@Serializable
enum class AuthorizationStrategyType {
  DELAY_AND_NOTIFY,
  HARDWARE_PROOF_OF_POSSESSION,
}

/**
 * Authorization strategy details - currently only supports delay and notify
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("authorization_strategy_type")
sealed class AuthorizationStrategy {
  @SerialName("authorization_strategy_type")
  abstract val authorizationStrategyType: AuthorizationStrategyType

  /**
   * Authorization strategy that requires a delay period before completion
   */
  @Serializable
  @SerialName("DELAY_AND_NOTIFY")
  data class DelayAndNotify(
    @SerialName("authorization_strategy_type")
    override val authorizationStrategyType: AuthorizationStrategyType,
    @SerialName("delay_start_time")
    val delayStartTime: Instant,
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
) : RedactedRequestBody

fun PrivilegedActionInstance.toPrimitive(): bitkey.privilegedactions.PrivilegedActionInstance {
  return bitkey.privilegedactions.PrivilegedActionInstance(
    id = id,
    privilegedActionType = privilegedActionType.toPrimitive(),
    authorizationStrategy = when (authorizationStrategy) {
      is AuthorizationStrategy.DelayAndNotify -> bitkey.privilegedactions.AuthorizationStrategy.DelayAndNotify(
        authorizationStrategyType = authorizationStrategy.authorizationStrategyType.toPrimitive(),
        delayStartTime = authorizationStrategy.delayStartTime,
        delayEndTime = authorizationStrategy.delayEndTime,
        cancellationToken = authorizationStrategy.cancellationToken,
        completionToken = authorizationStrategy.completionToken
      )
    }
  )
}

fun PrivilegedActionType.toPrimitive(): bitkey.privilegedactions.PrivilegedActionType {
  return when (this) {
    PrivilegedActionType.RESET_FINGERPRINT -> bitkey.privilegedactions.PrivilegedActionType.RESET_FINGERPRINT
  }
}

fun AuthorizationStrategyType.toPrimitive(): bitkey.privilegedactions.AuthorizationStrategyType {
  return when (this) {
    AuthorizationStrategyType.DELAY_AND_NOTIFY -> bitkey.privilegedactions.AuthorizationStrategyType.DELAY_AND_NOTIFY
    AuthorizationStrategyType.HARDWARE_PROOF_OF_POSSESSION -> bitkey.privilegedactions.AuthorizationStrategyType.HARDWARE_PROOF_OF_POSSESSION
  }
}
