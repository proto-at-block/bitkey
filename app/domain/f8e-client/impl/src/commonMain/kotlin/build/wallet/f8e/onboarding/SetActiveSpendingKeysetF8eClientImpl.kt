package build.wallet.f8e.onboarding

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.applyTo
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withAppAuthKey
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.recovery.SignedKeysetVerificationResponse
import build.wallet.ktor.result.EmptyRequestBody
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class SetActiveSpendingKeysetF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : SetActiveSpendingKeysetF8eClient {
  override suspend fun set(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    proof: PrivilegedActionProof,
  ): Result<SignedKeysetVerificationResponse?, NetworkingError> {
    return f8eHttpClient
      .authenticated()
      .bodyResult<SetActiveSpendingKeysetResponse> {
        put(urlString = "/api/accounts/${fullAccountId.serverId}/keysets/$keysetId") {
          withDescription("Set active spending keyset")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          withAppAuthKey(appAuthKey)
          proof.applyTo(this)
          setRedactedBody(EmptyRequestBody)
        }
      }
      .map { response -> response.toSignedKeysetVerification() }
  }
}

/**
 * Internal response model for PUT /keysets/:keyset_id.
 * For W3 accounts, all fields are present. For W1 accounts, all fields are null.
 */
@Serializable
private data class SetActiveSpendingKeysetResponse(
  @SerialName("app_auth_pub")
  val appAuthPub: String? = null,
  @SerialName("hardware_auth_pub")
  val hardwareAuthPub: String? = null,
  @SerialName("app_spending_pub")
  val appSpendingPub: String? = null,
  @SerialName("hardware_spending_pub")
  val hardwareSpendingPub: String? = null,
  @SerialName("server_spending_pub")
  val serverSpendingPub: String? = null,
  @SerialName("signature")
  val signature: String? = null,
) : RedactedResponseBody {
  /**
   * Converts to [SignedKeysetVerificationResponse] if all fields are present (W3),
   * or returns null if any field is missing (W1).
   */
  fun toSignedKeysetVerification(): SignedKeysetVerificationResponse? {
    // All fields must be present for W3 signed keyset verification
    val fields = listOf(appAuthPub, hardwareAuthPub, appSpendingPub, hardwareSpendingPub, serverSpendingPub, signature)
    if (fields.any { it == null }) return null

    return SignedKeysetVerificationResponse(
      appAuthPub = appAuthPub!!,
      hardwareAuthPub = hardwareAuthPub!!,
      appSpendingPub = appSpendingPub!!,
      hardwareSpendingPub = hardwareSpendingPub!!,
      serverSpendingPub = serverSpendingPub!!,
      signature = signature!!
    )
  }
}
