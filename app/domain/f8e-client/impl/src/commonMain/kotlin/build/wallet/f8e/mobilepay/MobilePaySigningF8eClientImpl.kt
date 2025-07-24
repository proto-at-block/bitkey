package build.wallet.f8e.mobilepay

import bitkey.verification.PendingTransactionVerification
import bitkey.verification.TxVerificationId
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
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientImpl.SignTransactionResponse.StatusCode.Companion.VERIFICATION_REQUESTED
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientImpl.SignTransactionResponse.StatusCode.Companion.VERIFICATION_REQUIRED
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayUnit
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.post
import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
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
  ): Result<Psbt, Error> {
    return f8eHttpClient.authenticated()
      .bodyResult<SignTransactionResponse> {
        post("/api/accounts/${fullAccountId.serverId}/keysets/$keysetId/sign-transaction") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            SignTransactionRequest(
              psbt = psbt.base64,
              prompt = false
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

  override suspend fun requestVerificationForSigning(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    fiatCurrency: FiatCurrency,
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<PendingTransactionVerification, Error> {
    return f8eHttpClient.authenticated()
      .bodyResult<SignTransactionResponse> {
        post("/api/accounts/${fullAccountId.serverId}/keysets/$keysetId/sign-transaction") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            SignTransactionRequest(
              psbt = psbt.base64,
              prompt = true,
              currency = fiatCurrency.textCode.code,
              bitcoinDisplayUnit = bitcoinDisplayUnit
            )
          )
        }
      }
      .flatMap { body ->
        when (body.status) {
          VERIFICATION_REQUESTED -> Ok(
            PendingTransactionVerification(
              id = requireNotNull(body.id),
              expiration = requireNotNull(body.expiration)
            )
          )
          else -> Err(Error("Unexpected status code: ${body.status?.code}"))
        }
      }
  }

  @Serializable
  private data class SignTransactionRequest(
    val psbt: String,
    @Unredacted
    @SerialName("should_prompt_user")
    val prompt: Boolean,
    @SerialName("fiat_currency")
    val currency: String? = null,
    @Unredacted
    @SerialName("bitcoin_display_unit")
    val bitcoinDisplayUnit: BitcoinDisplayUnit? = null,
  ) : RedactedRequestBody

  @Serializable
  private data class SignTransactionResponse(
    @SerialName("tx")
    val signedPsbt: String? = null,
    val status: StatusCode? = null,
    @Unredacted
    @SerialName("verification_id")
    val id: TxVerificationId? = null,
    @Unredacted
    @Serializable(with = InstantIso8601Serializer::class)
    val expiration: Instant? = null,
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
        val VERIFICATION_REQUESTED = StatusCode("VERIFICATION_REQUESTED")
        val VERIFICATION_REQUIRED = StatusCode("VERIFICATION_REQUIRED")
        val SIGNED = StatusCode("SIGNED")
      }
    }
  }
}
