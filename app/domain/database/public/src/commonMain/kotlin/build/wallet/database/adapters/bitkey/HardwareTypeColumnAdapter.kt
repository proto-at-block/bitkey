package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import bitkey.account.HardwareType

internal object HardwareTypeColumnAdapter : ColumnAdapter<HardwareType, String> {
  override fun decode(databaseValue: String): HardwareType = HardwareType.valueOf(databaseValue)

  override fun encode(value: HardwareType): String = value.name
}
