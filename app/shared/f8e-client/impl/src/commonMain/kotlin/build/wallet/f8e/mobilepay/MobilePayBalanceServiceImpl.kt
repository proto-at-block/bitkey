package build.wallet.f8e.mobilepay

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.bodyResult
import build.wallet.limit.MobilePayBalance
import build.wallet.limit.SpendingLimit
import build.wallet.logging.logNetworkFailure
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrencyDao
import build.wallet.money.currency.code.IsoCurrencyTextCode
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import com.ionspin.kotlin.bignum.integer.toBigInteger
import io.ktor.client.request.get
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class MobilePayBalanceServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val fiatCurrencyDao: FiatCurrencyDao,
) : MobilePayBalanceService {
  override suspend fun getMobilePayBalance(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<MobilePayBalance, MobilePayBalanceFailure> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .bodyResult<MobilePayBalanceDTO> {
        get("/api/accounts/${fullAccountId.serverId}/mobile-pay")
      }
      .logNetworkFailure { "Failed to get mobile pay balance" }
      .mapBoth(
        success = { body ->
          body.toMobilePayBalance()?.let { Ok(it) }
            ?: Err(MobilePayBalanceFailure.UnsupportedFiatCurrencyError)
        },
        failure = { Err(MobilePayBalanceFailure.F8eError(it)) }
      )
  }

  private suspend fun SpendingLimitDTO.toSpendingLimit(): SpendingLimit? =
    amount.toFiatMoney()?.let { fiatAmount ->
      SpendingLimit(
        active = active,
        amount = fiatAmount,
        timezone = TimeZone.of(timeZoneOffset)
      )
    }

  private suspend fun MobilePayBalanceDTO.toMobilePayBalance(): MobilePayBalance? =
    limit.toSpendingLimit()?.let { spendingLimit ->
      MobilePayBalance(
        spent = spent.toBitcoinMoney(),
        available = available.toBitcoinMoney(),
        limit = spendingLimit
      )
    }

  private fun MoneyDTO.toBitcoinMoney(): BitcoinMoney {
    require(currencyCode == "BTC")
    return BitcoinMoney(fractionalUnitAmount = amount.toBigInteger())
  }

  private suspend fun MoneyDTO.toFiatMoney() =
    fiatCurrencyDao.fiatCurrency(textCode = IsoCurrencyTextCode(currencyCode))
      .firstOrNull()?.let { fiatCurrency ->
        FiatMoney(
          currency = fiatCurrency,
          fractionalUnitAmount = amount.toBigInteger()
        )
      }
}

@Serializable
private data class MoneyDTO(
  val amount: Int,
  @SerialName("currency_code")
  val currencyCode: String,
)

@Serializable
private data class SpendingLimitDTO(
  val active: Boolean,
  val amount: MoneyDTO,
  @SerialName("time_zone_offset")
  val timeZoneOffset: String,
)

@Serializable
private data class MobilePayBalanceDTO(
  val spent: MoneyDTO,
  val available: MoneyDTO,
  val limit: SpendingLimitDTO,
)
