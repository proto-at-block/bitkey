package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitcoin.sync.ElectrumServerDetails

/**
 * SqlDelight column adapter for [ElectrumServerDetails].
 */
internal object ElectrumServerDetailsColumnAdapter : ColumnAdapter<ElectrumServerDetails, String> {
  override fun decode(databaseValue: String): ElectrumServerDetails {
    val parts = databaseValue.split(",")
    return when (parts.size) {
      1, 2 ->
        ElectrumServerDetails(
          host = parts.firstOrNull() ?: "",
          port = parts.lastOrNull() ?: ""
        )
      else -> ElectrumServerDetails(parts[0], parts[1], parts[2])
    }
  }

  override fun encode(value: ElectrumServerDetails): String {
    return "${value.protocol},${value.host},${value.port}"
  }
}
