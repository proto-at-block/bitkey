package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.crypto.CurveType

/**
 * Generic column adapter for any [SocRecKey] type.
 * @param factory A function that creates a new instance of the [SocRecKey] type from an [AppKey].
 * @param keyCurve The curve type of the key.
 */
internal class SocRecKeyColumnAdapter<T : SocRecKey>(
  private val factory: (AppKey) -> T,
  private val keyCurve: CurveType,
) : ColumnAdapter<T, String> {
  override fun decode(databaseValue: String) =
    factory(AppKey.fromPublicKey(databaseValue).copy(curveType = keyCurve))

  override fun encode(value: T) = value.publicKey.value
}
