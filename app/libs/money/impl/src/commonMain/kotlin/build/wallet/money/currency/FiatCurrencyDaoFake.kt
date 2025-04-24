package build.wallet.money.currency

import build.wallet.money.currency.code.IsoCurrencyTextCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FiatCurrencyDaoFake : FiatCurrencyDao {
  private val currencies: MutableStateFlow<List<FiatCurrency>> = MutableStateFlow(emptyList())

  override fun allFiatCurrencies(): Flow<List<FiatCurrency>> = currencies

  override fun fiatCurrency(textCode: IsoCurrencyTextCode): Flow<FiatCurrency?> {
    return currencies.map { it.find { it.textCode == textCode } }
  }

  override suspend fun storeFiatCurrencies(fiatCurrencies: List<FiatCurrency>) {
    currencies.value = fiatCurrencies
  }
}
