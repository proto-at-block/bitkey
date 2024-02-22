package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.catching
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOr
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object MobilePaySnapValueColumnAdapter : ColumnAdapter<Map<Int, Int>, String> {
  override fun decode(databaseValue: String): Map<Int, Int> {
    return Result.catching {
      Json.decodeFromString<Map<Int, Int>>(databaseValue)
    }.getOr(emptyMap())
  }

  override fun encode(value: Map<Int, Int>) = Json.encodeToString(value)
}
