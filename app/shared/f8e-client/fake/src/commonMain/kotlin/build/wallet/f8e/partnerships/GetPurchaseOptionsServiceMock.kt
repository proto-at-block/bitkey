package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetPurchaseOptionsServiceMock(
  turbine: (String) -> Turbine<Any>,
) : GetPurchaseOptionsService {
  val getPurchaseOptionsServiceCall = turbine("get purchase options")

  private val successResult: Result<PurchaseOptions, NetworkingError> =
    Ok(
      PurchaseOptions(
        country = "US",
        fiatCurrency = "USD",
        paymentMethods =
          mapOf(
            "CARD" to
              PaymentMethodOptions(
                displayPurchaseAmounts =
                  listOf(
                    10.0,
                    25.0,
                    50.0,
                    100.0,
                    200.0
                  ),
                defaultPurchaseAmount = 100.0,
                minPurchaseAmount = 10.0,
                maxPurchaseAmount = 500.0
              )
          )
      )
    )

  private val notAvailableResult =
    Ok(
      PurchaseOptions(
        country = "GB",
        fiatCurrency = "GBP",
        paymentMethods = emptyMap()
      )
    )

  override suspend fun purchaseOptions(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    currency: FiatCurrency,
  ): Result<PurchaseOptions, NetworkingError> {
    getPurchaseOptionsServiceCall.add(Unit)

    return if (currency == USD) {
      successResult
    } else {
      notAvailableResult
    }
  }
}
