package build.wallet.f8e.money

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MoneyDTO(
  val amount: Int,
  @SerialName("currency_code")
  val currencyCode: String,
)
