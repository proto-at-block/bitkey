package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.relationships.RelationshipId

object RelationshipIdColumnAdapter : ColumnAdapter<RelationshipId, String> {
  override fun decode(databaseValue: String): RelationshipId {
    return RelationshipId(value = databaseValue)
  }

  override fun encode(value: RelationshipId): String {
    return value.value
  }
}
