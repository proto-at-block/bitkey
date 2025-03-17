package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.email.Email

/**
 * SqlDelight column adapter for [Email].
 *
 * Encodes as [Email.value].
 */
internal object EmailColumnAdapter : ColumnAdapter<Email, String> {
  override fun decode(databaseValue: String): Email {
    return Email(databaseValue)
  }

  override fun encode(value: Email): String {
    return value.value
  }
}
