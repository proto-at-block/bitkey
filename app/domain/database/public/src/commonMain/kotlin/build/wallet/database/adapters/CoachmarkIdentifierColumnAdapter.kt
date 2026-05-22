package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.coachmark.CoachmarkIdentifier

/**
 * Persists coachmarks by enum constant name while remaining backward-compatible with newer rows
 * stored as stable [CoachmarkIdentifier.id] strings.
 */
internal object CoachmarkIdentifierColumnAdapter : ColumnAdapter<CoachmarkIdentifier, String> {
  override fun decode(databaseValue: String): CoachmarkIdentifier =
    CoachmarkIdentifier.fromDatabaseValue(databaseValue)

  override fun encode(value: CoachmarkIdentifier): String = value.name
}
