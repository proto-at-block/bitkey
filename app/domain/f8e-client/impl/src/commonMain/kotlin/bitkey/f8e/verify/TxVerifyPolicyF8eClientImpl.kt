package bitkey.f8e.verify

import bitkey.verification.TxVerificationPolicy
import bitkey.verification.VerificationThreshold
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.client.plugins.withHardwareFactor
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.money.MoneyDTO
import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.setRedactedBody
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.FiatCurrencyDao
import build.wallet.money.currency.code.IsoCurrencyTextCode
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import com.ionspin.kotlin.bignum.integer.toBigInteger
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.get
import io.ktor.client.request.put
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class TxVerifyPolicyF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val fiatCurrencyDao: FiatCurrencyDao,
) : TxVerifyPolicyF8eClient {
  override suspend fun setPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    threshold: VerificationThreshold,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<TxVerificationPolicy.DelayNotifyAuthorization?, Error> {
    return f8eHttpClient.authenticated()
      .bodyResult<PolicyChangeResponse> {
        put("/api/accounts/${fullAccountId.serverId}/tx-verify/policy") {
          withHardwareFactor(hwFactorProofOfPossession)
          withDescription("Set Tx Verification Policy")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            PolicyChangeRequest(
              state = when (threshold) {
                VerificationThreshold.Disabled -> VerificationState.NEVER
                VerificationThreshold.Always -> VerificationState.ALWAYS
                else -> VerificationState.THRESHOLD
              },
              threshold = threshold.amount
                .takeIf { threshold != VerificationThreshold.Always }
                ?.let {
                  MoneyDTO(
                    amount = it.fractionalUnitValue.intValue(),
                    currencyCode = it.currency.textCode.code
                  )
                }
            )
          )
        }
      }.map { response ->
        if (response.id == null || response.strategy == null) {
          null
        } else {
          TxVerificationPolicy.DelayNotifyAuthorization(
            id = TxVerificationPolicy.DelayNotifyAuthorization.AuthId(response.id),
            delayEndTime = response.strategy.endTime,
            cancellationToken = response.strategy.cancellationToken,
            completionToken = response.strategy.completionToken
          )
        }
      }
  }

  override suspend fun getPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<VerificationThreshold, Error> {
    return f8eHttpClient.authenticated()
      .bodyResult<ThresholdResponse> {
        get("/api/accounts/${fullAccountId.serverId}/tx-verify/policy") {
          withDescription("Get Tx Verification Policy")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
        }
      }.flatMap { response ->
        when (response.threshold) {
          null -> VerificationThreshold.Disabled
          else -> VerificationThreshold.Enabled(
            response.threshold.let { threshold ->
              when (threshold.currencyCode) {
                BTC.textCode.code -> BitcoinMoney(
                  fractionalUnitAmount = threshold.amount.toBigInteger()
                )
                else -> FiatMoney(
                  currency = fiatCurrencyDao.fiatCurrency(
                    textCode = IsoCurrencyTextCode(threshold.currencyCode)
                  ).first() ?: return@flatMap Err(Error("Unsupported currency: ${threshold.currencyCode}")),
                  fractionalUnitAmount = threshold.amount.toBigInteger()
                )
              }
            }
          )
        }.let { Ok(it) }
      }
  }
}

@Serializable
private data class PolicyChangeRequest(
  @Unredacted
  val state: VerificationState,
  val threshold: MoneyDTO?,
) : RedactedRequestBody

@Serializable
private data class PolicyChangeResponse(
  @Unredacted
  val id: String? = null,
  @SerialName("authorization_strategy")
  val strategy: AuthStrategy? = null,
) : RedactedResponseBody {
  @Serializable
  data class AuthStrategy(
    @SerialName("delay_end_time")
    val endTime: Instant,
    @SerialName("cancellation_token")
    val cancellationToken: String,
    @SerialName("completion_token")
    val completionToken: String,
  )
}

@Serializable
private data class ThresholdResponse(
  val threshold: MoneyDTO?,
) : RedactedResponseBody

@Serializable
private enum class VerificationState {
  NEVER,
  THRESHOLD,
  ALWAYS,
}
