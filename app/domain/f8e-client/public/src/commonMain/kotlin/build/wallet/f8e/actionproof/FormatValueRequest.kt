package build.wallet.f8e.actionproof

import build.wallet.ktor.result.RedactedRequestBody
import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Request to format a display value for an action proof.
 * Each subclass represents an action type with its specific fields.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("action")
sealed interface FormatValueRequest : RedactedRequestBody {
  @Serializable
  @SerialName("set_spend_without_hardware")
  data class SetSpendWithoutHardware(
    val amount: ULong,
    @SerialName("currency_code")
    val currencyCode: IsoCurrencyTextCode,
    val locale: String,
  ) : FormatValueRequest
}
