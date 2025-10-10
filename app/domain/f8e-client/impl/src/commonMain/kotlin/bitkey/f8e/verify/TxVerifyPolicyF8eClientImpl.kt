package bitkey.f8e.verify

import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.toPrimitive
import bitkey.verification.TxVerificationPolicy
import bitkey.verification.VerificationThreshold
import bitkey.verification.VerificationThreshold.Companion.Always
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
import com.github.michaelbull.result.*
import com.ionspin.kotlin.bignum.integer.toBigInteger
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.get
import io.ktor.client.request.put
import kotlinx.coroutines.flow.first
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@BitkeyInject(AppScope::class)
class TxVerifyPolicyF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val fiatCurrencyDao: FiatCurrencyDao,
) : TxVerifyPolicyF8eClient {
  override suspend fun setPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    policy: TxVerificationPolicy,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<TxVerificationPolicy, Error> {
    return f8eHttpClient.authenticated()
      .bodyResult<SetPolicyResponse> {
        put("/api/accounts/${fullAccountId.serverId}/tx-verify/policy") {
          withHardwareFactor(hwFactorProofOfPossession)
          withDescription("Set Tx Verification Policy")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(
            PolicyChangeRequest(
              state = when (policy) {
                is TxVerificationPolicy.Active -> when (policy.threshold) {
                  Always -> VerificationState.ALWAYS
                  else -> VerificationState.THRESHOLD
                }
                TxVerificationPolicy.Disabled -> VerificationState.NEVER
                is TxVerificationPolicy.Pending -> error("Can't set a pending policy directly")
              },
              threshold = (policy as? TxVerificationPolicy.Active)
                ?.threshold
                ?.amount
                ?.takeIf { policy.threshold != Always }
                ?.let {
                  MoneyDTO(
                    amount = it.fractionalUnitValue.ulongValue(exactRequired = true),
                    currencyCode = it.currency.textCode.code
                  )
                }
            )
          )
        }
      }.map { response ->
        when (response) {
          is SetPolicyResponse.EmptyResponse -> policy
          is SetPolicyResponse.PrivilegedActionInstanceResponse ->
            response.privilegedActionInstance
              .let { TxVerificationPolicy.Pending(it.toPrimitive()) }
        }
      }
  }

  override suspend fun getPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<TxVerificationPolicy?, Error> {
    return f8eHttpClient.authenticated()
      .bodyResult<ThresholdResponse> {
        get("/api/accounts/${fullAccountId.serverId}/tx-verify/policy") {
          withDescription("Get Tx Verification Policy")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
        }
      }.flatMap { response ->
        when (response.threshold) {
          null -> TxVerificationPolicy.Disabled
          else -> TxVerificationPolicy.Active(
            threshold = VerificationThreshold(
              amount = response.threshold.let { threshold ->
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
private data class ThresholdResponse(
  val threshold: MoneyDTO?,
) : RedactedResponseBody

@Serializable
private enum class VerificationState {
  NEVER,
  THRESHOLD,
  ALWAYS,
}

@Serializable(with = SetPolicyResponseSerializer::class)
private sealed interface SetPolicyResponse : RedactedResponseBody {
  @Serializable
  data object EmptyResponse : SetPolicyResponse

  @Serializable
  data class PrivilegedActionInstanceResponse(
    @SerialName("privileged_action_instance")
    val privilegedActionInstance: PrivilegedActionInstance,
  ) : SetPolicyResponse
}

private object SetPolicyResponseSerializer : JsonContentPolymorphicSerializer<SetPolicyResponse>(
  SetPolicyResponse::class
) {
  override fun selectDeserializer(
    element: JsonElement,
  ): DeserializationStrategy<SetPolicyResponse> {
    return when (element.jsonObject["privileged_action_instance"]) {
      null -> SetPolicyResponse.EmptyResponse.serializer()
      else -> SetPolicyResponse.PrivilegedActionInstanceResponse.serializer()
    }
  }
}
