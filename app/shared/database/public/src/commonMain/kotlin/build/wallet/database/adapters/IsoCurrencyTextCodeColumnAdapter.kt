package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.money.currency.code.IsoCurrencyTextCode

internal object IsoCurrencyTextCodeColumnAdapter : ColumnAdapter<IsoCurrencyTextCode, String> {
  override fun decode(databaseValue: String) = IsoCurrencyTextCode(databaseValue)

  override fun encode(value: IsoCurrencyTextCode) = value.code
}
