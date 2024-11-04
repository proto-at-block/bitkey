package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.inheritance.InheritanceClaimId

object InheritanceClaimIdColumnAdapter : ColumnAdapter<InheritanceClaimId, String> {
  override fun decode(databaseValue: String): InheritanceClaimId {
    return InheritanceClaimId(value = databaseValue)
  }

  override fun encode(value: InheritanceClaimId): String {
    return value.value
  }
}
