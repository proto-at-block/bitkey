package bitkey.f8e.verify

import bitkey.verification.PendingTransactionVerification
import bitkey.verification.TxVerificationApproval
import bitkey.verification.TxVerificationId
import bitkey.verification.TxVerificationState
import bitkey.verification.VerificationRequiredError
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.catching
import build.wallet.ktor.result.setRedactedBody
import build.wallet.mapUnit
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayUnit
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantIso8601Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@BitkeyInject(AppScope::class)
class TxVerificationF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : TxVerificationF8eClient {
  override suspend fun createVerificationRequest(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    fiatCurrency: FiatCurrency,
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<PendingTransactionVerification, Throwable> {
    return f8eHttpClient.authenticated()
      .bodyResult<CreateVerificationResponse> {
        post("/api/accounts/${fullAccountId.serverId}/tx-verify/requests") {
          withDescription("Create Tx Verification Request")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            CreateVerificationRequest(
              psbt = psbt.base64,
              currency = fiatCurrency.textCode.code,
              bitcoinDisplayUnit = bitcoinDisplayUnit,
              prompt = true,
              signingKeysetId = keysetId
            )
          )
        }
      }.flatMap {
        when (it.status) {
          CreateVerificationResponse.StatusCode.VERIFICATION_REQUESTED -> Ok(
            PendingTransactionVerification(
              id = requireNotNull(it.id),
              expiration = requireNotNull(it.expiration)
            )
          )
          else -> Err(IllegalStateException("Unexpected status when creating a request: ${it.status}"))
        }
      }
  }

  override suspend fun getVerificationStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationId: TxVerificationId,
  ): Result<TxVerificationState, Throwable> {
    return f8eHttpClient.authenticated()
      .bodyResult<GetStatusResponse> {
        get("/api/accounts/${fullAccountId.serverId}/tx-verify/requests/${verificationId.value}") {
          withDescription("Get Tx Verification Status")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
        }
      }.flatMap {
        when (it.status) {
          GetStatusResponse.StatusCode.PENDING -> Ok(
            TxVerificationState.Pending
          )
          GetStatusResponse.StatusCode.SUCCESS -> Ok(
            TxVerificationState.Success(
              hardwareGrant = requireNotNull(it.hwGrant)
            )
          )
          GetStatusResponse.StatusCode.FAILED -> Ok(
            TxVerificationState.Failed
          )
          GetStatusResponse.StatusCode.EXPIRED -> Ok(
            TxVerificationState.Expired
          )
          else -> Err(IllegalStateException("Unexpected status when getting verification status: ${it.status}"))
        }
      }
  }

  override suspend fun cancelVerification(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationId: TxVerificationId,
  ): Result<Unit, Throwable> {
    return f8eHttpClient.authenticated()
      .catching {
        delete("/api/accounts/${fullAccountId.serverId}/tx-verify/requests/${verificationId.value}") {
          withDescription("Cancel Tx Verification")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
        }
      }.mapUnit()
  }

  override suspend fun requestGrant(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    fiatCurrency: FiatCurrency,
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<TxVerificationApproval, Throwable> {
    return f8eHttpClient.authenticated()
      .bodyResult<CreateVerificationResponse> {
        post("/api/accounts/${fullAccountId.serverId}/tx-verify/requests") {
          withDescription("Create Tx Verification Request")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            CreateVerificationRequest(
              psbt = psbt.base64,
              currency = fiatCurrency.textCode.code,
              bitcoinDisplayUnit = bitcoinDisplayUnit,
              prompt = false,
              signingKeysetId = keysetId
            )
          )
        }
      }.flatMap {
        when (it.status) {
          CreateVerificationResponse.StatusCode.SIGNED -> Ok(requireNotNull(it.hwGrant))
          CreateVerificationResponse.StatusCode.VERIFICATION_REQUIRED -> Err(VerificationRequiredError)
          else -> Err(IllegalStateException("Unexpected status when creating a request: ${it.status}"))
        }
      }
  }
}

/**
 * Server format for the creating a verification request
 */
@Serializable
private data class CreateVerificationRequest(
  val psbt: String,
  @SerialName("fiat_currency")
  val currency: String,
  @Unredacted
  @SerialName("bitcoin_display_unit")
  val bitcoinDisplayUnit: BitcoinDisplayUnit,
  @Unredacted
  @SerialName("should_prompt_user")
  val prompt: Boolean,
  @SerialName("signing_keyset_id")
  val signingKeysetId: String,
) : RedactedRequestBody

/**
 * Server response after creating a verification request.
 */
@Serializable
private data class CreateVerificationResponse(
  @Unredacted
  val status: StatusCode,
  @Unredacted
  @SerialName("verification_id")
  val id: TxVerificationId? = null,
  @Unredacted
  @Serializable(with = InstantIso8601Serializer::class)
  val expiration: Instant? = null,
  @SerialName("hw_grant")
  val hwGrant: TxVerificationApproval? = null,
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

/**
 * Server response for getting the current state of a verification request.
 *
 * This is used as a discriminator key for polymorphic response deserialization.
 */
@Serializable
private data class GetStatusResponse(
  @Unredacted
  val status: StatusCode,
  @Unredacted
  @SerialName("verification_id")
  val id: TxVerificationId? = null,
  @Unredacted
  @Serializable(with = InstantIso8601Serializer::class)
  val expiration: Instant? = null,
  @SerialName("hw_grant")
  val hwGrant: TxVerificationApproval? = null,
) : RedactedResponseBody {
  /**
   * Possible states of the verification
   */
  @JvmInline
  @Serializable
  value class StatusCode(val code: String) {
    companion object {
      val PENDING = StatusCode("PENDING")
      val SUCCESS = StatusCode("SUCCESS")
      val EXPIRED = StatusCode("EXPIRED")
      val FAILED = StatusCode("FAILED")
    }
  }
}
