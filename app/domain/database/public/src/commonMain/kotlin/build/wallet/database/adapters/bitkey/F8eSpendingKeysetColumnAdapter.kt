package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import bitkey.serialization.json.decodeFromStringResult
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object F8eSpendingKeysetColumnAdapter : ColumnAdapter<F8eSpendingKeyset, String> {
  /**
   * IMPORTANT - Do NOT add a default values for `F8eSpendingKeyset`.
   *
   * From the docs for explicitNulls:
   *
   * Exercise extra caution if you want to use this flag and have non-typical classes with properties that are nullable,
   * but have default value that is not null. In that case, encoding and decoding will not be symmetrical if null is omitted
   * from the output.
   */
  val json = Json { explicitNulls = false }

  override fun decode(databaseValue: String): F8eSpendingKeyset {
    return json
      .decodeFromStringResult<F8eSpendingKeyset>(databaseValue)
      .map(::validate)
      .getOrThrow()
  }

  override fun encode(value: F8eSpendingKeyset): String {
    val sanitized = validate(value)
    return json.encodeToString(sanitized)
  }

  private fun validate(value: F8eSpendingKeyset): F8eSpendingKeyset {
    require(value.keysetId.isNotBlank()) { "F8eSpendingKeyset must have a non-blank keyset id." }

    val spendingPublicKeyDpub = value.spendingPublicKey.key.dpub
    require(spendingPublicKeyDpub.isNotBlank()) {
      "F8eSpendingKeyset must have a non-blank spending public key."
    }

    value.privateWalletRootXpub?.let {
      require(it.isNotBlank()) { "Private wallet root xpub must be non-blank when present." }
    }

    return value
  }
}
