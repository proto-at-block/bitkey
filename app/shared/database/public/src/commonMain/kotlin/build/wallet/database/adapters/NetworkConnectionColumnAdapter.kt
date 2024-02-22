package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.availability.NetworkConnection
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.runCatching
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SqlDelight column adapter for [NetworkConnection].
 */
internal object NetworkConnectionColumnAdapter : ColumnAdapter<NetworkConnection, String> {
  override fun decode(databaseValue: String): NetworkConnection {
    return runCatching { Json.decodeFromString<NetworkConnection>(databaseValue) }
      .getOr(NetworkConnection.UnknownNetworkConnection)
  }

  override fun encode(value: NetworkConnection): String {
    return Json.encodeToString(value)
  }
}
