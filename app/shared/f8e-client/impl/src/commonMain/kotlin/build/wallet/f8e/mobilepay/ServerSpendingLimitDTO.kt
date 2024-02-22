package build.wallet.f8e.mobilepay

import build.wallet.f8e.mobilepay.ServerSpendingLimitDTO.MoneyDTO
import build.wallet.limit.SpendingLimit
import build.wallet.time.timeFromUtcInHms
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DTO for sending a [SpendingLimit] to the server */
@Serializable
data class ServerSpendingLimitDTO(
  val active: Boolean,
  val amount: MoneyDTO,
  @SerialName("time_zone_offset")
  val timeZoneOffset: String,
) {
  @Serializable
  data class MoneyDTO(
    val amount: Int,
    @SerialName("currency_code")
    val currencyCode: String,
  )
}

fun SpendingLimit.toServerSpendingLimit(clock: Clock = Clock.System): ServerSpendingLimitDTO =
  ServerSpendingLimitDTO(
    active = active,
    amount =
      MoneyDTO(
        amount = this.amount.fractionalUnitValue.intValue(),
        currencyCode = this.amount.currency.textCode.code
      ),
    timeZoneOffset = this.timezone.timeFromUtcInHms(clock)
  )
