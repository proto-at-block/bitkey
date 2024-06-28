package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.availability.NetworkConnection
import build.wallet.serialization.json.decodeFromStringResult
import com.github.michaelbull.result.getOr
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SqlDelight column adapter for [NetworkConnection].
 */
internal object NetworkConnectionColumnAdapter : ColumnAdapter<NetworkConnection, String> {
  override fun decode(databaseValue: String): NetworkConnection {
    return Json.decodeFromStringResult<NetworkConnection>(databaseValue)
      .getOr(NetworkConnection.UnknownNetworkConnection)
  }

  override fun encode(value: NetworkConnection): String {
    return Json.encodeToString(value)
  }
}
