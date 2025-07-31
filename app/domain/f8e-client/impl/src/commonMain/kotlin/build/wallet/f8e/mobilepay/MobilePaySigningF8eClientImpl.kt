package build.wallet.f8e.mobilepay

import bitkey.verification.TxVerificationApproval
import bitkey.verification.VerificationRequiredError
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientImpl.SignTransactionResponse.StatusCode.Companion.SIGNED
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientImpl.SignTransactionResponse.StatusCode.Companion.VERIFICATION_REQUIRED
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import io.ktor.client.request.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@BitkeyInject(AppScope::class)
class MobilePaySigningF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : MobilePaySigningF8eClient {
  override suspend fun signWithSpecificKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    grant: TxVerificationApproval?,
  ): Result<Psbt, Error> {
    return f8eHttpClient.authenticated()
      .bodyResult<SignTransactionResponse> {
        post("/api/accounts/${fullAccountId.serverId}/keysets/$keysetId/sign-transaction") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            SignTransactionRequest(
              psbt = psbt.base64,
              grant = grant
            )
          )
        }
      }
      .flatMap { body ->
        when (body.status) {
          null, SIGNED -> Ok(psbt.copy(base64 = requireNotNull(body.signedPsbt)))
          VERIFICATION_REQUIRED -> Err(VerificationRequiredError)
          else -> Err(Error("Unexpected status code: ${body.status.code}"))
        }
      }
  }

  @Serializable
  private data class SignTransactionRequest(
    val psbt: String,
    val grant: TxVerificationApproval? = null,
  ) : RedactedRequestBody

  @Serializable
  private data class SignTransactionResponse(
    @SerialName("tx")
    val signedPsbt: String? = null,
    val status: StatusCode? = null,
  ) : RedactedResponseBody {
    /**
     * Possible response status codes when starting a verification.
     *
     * This is used as a discriminator key for polymorphic response deserialization.
     */
    @JvmInline
    @Serializable
    value class StatusCode(val code: String) {
      companion object {
        val VERIFICATION_REQUIRED = StatusCode("VERIFICATION_REQUIRED")
        val SIGNED = StatusCode("SIGNED")
      }
    }
  }
}
