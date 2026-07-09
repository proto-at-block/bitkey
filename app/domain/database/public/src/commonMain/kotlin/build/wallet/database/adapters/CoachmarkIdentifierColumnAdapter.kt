package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.coachmark.CoachmarkIdentifier

/**
 * Persists coachmarks by stable [CoachmarkIdentifier.id].
 */
internal object CoachmarkIdentifierColumnAdapter : ColumnAdapter<CoachmarkIdentifier, String> {
  override fun decode(databaseValue: String): CoachmarkIdentifier =
    CoachmarkIdentifier.fromDatabaseValue(databaseValue)

  override fun encode(value: CoachmarkIdentifier): String = value.id
}
