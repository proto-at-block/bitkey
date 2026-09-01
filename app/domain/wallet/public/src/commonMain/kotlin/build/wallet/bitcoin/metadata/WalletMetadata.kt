package build.wallet.bitcoin.metadata

import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitkey.f8e.AccountId
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

/**
 * Identifies the account that wallet metadata (e.g. transaction notes) belongs to.
 *
 * Wraps the account's server id so that metadata storage is keyed consistently
 * regardless of the [AccountId] subtype it originated from.
 */
@JvmInline
value class WalletMetadataAccountId(val value: String) {
  init {
    require(value.isNotBlank()) { "Account id cannot be blank." }
  }

  companion object {
    fun fromAccountId(accountId: AccountId): WalletMetadataAccountId =
      WalletMetadataAccountId(accountId.serverId)
  }
}

/**
 * Records the deletion of a piece of wallet metadata so that deletions can be
 * propagated when metadata is later synced or backed up (see the server backup work
 * stacked on this change). Written by the DAO whenever a note is deleted.
 */
sealed interface WalletMetadataTombstone {
  val deletedAt: Instant

  data class DeletedTransactionNote(
    val transactionId: BitcoinTransactionId,
    override val deletedAt: Instant,
  ) : WalletMetadataTombstone
}

/**
 * A customer-authored note attached to an on-chain transaction.
 *
 * The note content is customer-controlled and potentially sensitive (it can name
 * counterparties, amounts, or purposes), so the type is [Redacted] from `toString()`.
 */
@Redacted
data class TransactionNote(
  val transactionId: BitcoinTransactionId,
  val note: String,
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  init {
    require(note.isNotBlank()) { "Transaction note cannot be blank." }
    require(note.length <= MAX_NOTE_LENGTH) {
      "Transaction note cannot exceed $MAX_NOTE_LENGTH characters."
    }
    require(updatedAt >= createdAt) {
      "Transaction note updatedAt cannot be before createdAt."
    }
  }

  companion object {
    const val MAX_NOTE_LENGTH = 1024

    /**
     * Normalizes raw customer input into a valid note value, or returns
     * [TransactionNoteServiceError.InvalidNote] when the input cannot be a valid note.
     *
     * Shared by production implementations and fakes so validation rules cannot drift.
     */
    fun normalize(note: String): Result<String, TransactionNoteServiceError.InvalidNote> {
      val normalized = note.trim()
      return when {
        normalized.isBlank() ->
          Err(
            TransactionNoteServiceError.InvalidNote(
              message = "Transaction note cannot be blank."
            )
          )
        normalized.length > MAX_NOTE_LENGTH ->
          Err(
            TransactionNoteServiceError.InvalidNote(
              message = "Transaction note cannot exceed $MAX_NOTE_LENGTH characters."
            )
          )
        else -> Ok(normalized)
      }
    }
  }
}


/**
 * A point-in-time wallet metadata set used for account-level backup and restore.
 */
data class WalletMetadataSnapshot(
  val version: UInt = CURRENT_VERSION,
  val accountId: WalletMetadataAccountId,
  val transactionNotes: Set<TransactionNote> = emptySet(),
  val tombstones: Set<WalletMetadataTombstone> = emptySet(),
  val updatedAt: Instant = walletMetadataUpdatedAt(transactionNotes, tombstones)
    ?: Instant.fromEpochMilliseconds(0),
) {
  companion object {
    const val CURRENT_VERSION: UInt = 1u
  }
}

fun walletMetadataUpdatedAt(
  transactionNotes: Set<TransactionNote>,
  tombstones: Set<WalletMetadataTombstone>,
): Instant? =
  (transactionNotes.map { it.updatedAt } + tombstones.map { it.deletedAt }).maxOrNull()

fun mergeWalletMetadataSnapshots(
  first: WalletMetadataSnapshot?,
  second: WalletMetadataSnapshot?,
): WalletMetadataSnapshot? {
  val accountId = first?.accountId ?: second?.accountId ?: return null
  require(first == null || first.accountId == accountId) { "Snapshot account IDs must match." }
  require(second == null || second.accountId == accountId) { "Snapshot account IDs must match." }

  val notesByTransactionId = (first?.transactionNotes.orEmpty() + second?.transactionNotes.orEmpty())
    .groupBy { it.transactionId }
    .mapValues { (_, notes) -> notes.maxBy { it.updatedAt } }
  val tombstonesByTransactionId =
    (first?.tombstones.orEmpty() + second?.tombstones.orEmpty())
      .filterIsInstance<WalletMetadataTombstone.DeletedTransactionNote>()
      .groupBy { it.transactionId }
      .mapValues { (_, tombstones) -> tombstones.maxBy { it.deletedAt } }

  val mergedNotes = notesByTransactionId.values
    .filter { note ->
      val tombstone = tombstonesByTransactionId[note.transactionId]
      tombstone == null || note.updatedAt > tombstone.deletedAt
    }
    .toSet()
  val mergedTombstones = tombstonesByTransactionId.values
    .filter { tombstone ->
      val note = notesByTransactionId[tombstone.transactionId]
      note == null || tombstone.deletedAt >= note.updatedAt
    }
    .toSet()

  return WalletMetadataSnapshot(
    version = maxOf(
      first?.version ?: WalletMetadataSnapshot.CURRENT_VERSION,
      second?.version ?: WalletMetadataSnapshot.CURRENT_VERSION
    ),
    accountId = accountId,
    transactionNotes = mergedNotes,
    tombstones = mergedTombstones,
    updatedAt = walletMetadataUpdatedAt(mergedNotes, mergedTombstones)
      ?: first?.updatedAt
      ?: second?.updatedAt
      ?: Instant.fromEpochMilliseconds(0)
  )
}
