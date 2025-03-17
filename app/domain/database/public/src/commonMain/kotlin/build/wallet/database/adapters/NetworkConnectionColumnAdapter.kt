package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import bitkey.serialization.json.decodeFromStringResult
import build.wallet.availability.NetworkConnection
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
