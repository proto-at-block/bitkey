package bitkey.recovery

import bitkey.f8e.account.WalletMetadataBackup
import build.wallet.bitcoin.metadata.WalletMetadataAccountId
import build.wallet.bitcoin.metadata.WalletMetadataSnapshot
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Ssek
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
interface WalletMetadataServerBackupService {
  /** Emits true while a server metadata restore attempt is in progress. */
  val isRestoring: StateFlow<Boolean>

  /** Foreground action required to make metadata sync possible; impossible states do not retry. */
  val remediation: StateFlow<WalletMetadataRemediation>

  /**
   * Records the account-level sealed SSEK to use for standalone metadata backups.
   * This is intentionally independent from descriptor-backup presence.
   */
  suspend fun recordSealedSsek(
    accountId: WalletMetadataAccountId,
    sealedSsek: SealedSsek,
  ): Result<Unit, Error>

  /**
   * Records [sealedSsek] and durably schedules a snapshot upload, including when the account has no
   * metadata yet. Repeated calls are safe and are used by onboarding and SSEK-rotation flows.
   *
   * [f8eEnvironment] is explicit because onboarding provisions before the account is active, when
   * ambient config could fall back to the default environment instead of the account's.
   */
  suspend fun provision(
    accountId: FullAccountId,
    sealedSsek: SealedSsek,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, Error>

  /**
   * Marks the account's local wallet metadata as needing server backup. The background worker will
   * retry until backup succeeds. This must not fail or roll back local metadata edits.
   */
  suspend fun requestBackup(accountId: WalletMetadataAccountId): Result<Unit, Error>

  /**
   * Pulls the active account's remote metadata, merges it locally, then uploads the merged union
   * when needed. Startup, periodic, and immediate triggers all use this serialized sync pass.
   */
  suspend fun syncPendingBackups(): Result<Unit, Error>

  /**
   * Fetches the standalone metadata backup using an explicit environment. Recovery flows use this
   * before the account becomes active so they cannot rely on ambient account configuration.
   */
  suspend fun fetchBackup(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
  ): Result<WalletMetadataBackup?, Error>

  /** Resolves the current SSEK from metadata first, then the descriptor-backup source of truth. */
  suspend fun resolveSealedSsek(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    metadataBackup: WalletMetadataBackup?,
  ): Result<SealedSsek?, Error>

  /** Returns true when [sealedSsek]'s plaintext key is already available locally. */
  suspend fun hasSsek(sealedSsek: SealedSsek): Result<Boolean, Error>

  /** Persists an SSEK unsealed by hardware and schedules metadata provisioning/restoration work. */
  suspend fun recordUnsealedSsek(
    accountId: FullAccountId,
    sealedSsek: SealedSsek,
    ssek: Ssek,
  ): Result<Unit, Error>

  /** Completes foreground remediation by storing the key and immediately restoring remote data. */
  suspend fun remediateWithUnsealedSsek(
    accountId: FullAccountId,
    sealedSsek: SealedSsek,
    ssek: Ssek,
  ): Result<WalletMetadataSnapshot?, Error>

  /**
   * Restores account-level wallet metadata from the standalone server backup, if present.
   * Missing backups and restore failures are non-fatal to wallet recovery.
   */
  suspend fun restoreFromServerBackup(
    accountId: FullAccountId,
  ): Result<WalletMetadataSnapshot?, Error>

  /**
   * Restores account-level wallet metadata from an already-fetched [backup] payload without
   * making a network request. Reuses the same unseal, account-ID guard, DAO merge, and
   * [isRestoring] lifecycle as [restoreFromServerBackup].
   *
   * Intended for use after hardware auth when the backup was prefetched during [completeAuth]
   * so that no network call occurs while an NFC session is open.
   *
   * Missing backups and restore failures are non-fatal to wallet recovery.
   */
  suspend fun restoreFromPrefetchedBackup(
    accountId: FullAccountId,
    backup: WalletMetadataBackup,
  ): Result<WalletMetadataSnapshot?, Error>
}

sealed interface WalletMetadataRemediation {
  data object None : WalletMetadataRemediation

  data class HardwareUnsealRequired(
    val accountId: FullAccountId,
    val sealedSsek: SealedSsek,
    /** Key that should encrypt the final backup after [sealedSsek] unlocks old remote data. */
    val targetSealedSsek: SealedSsek = sealedSsek,
  ) : WalletMetadataRemediation

  data class Unsupported(
    val accountId: FullAccountId,
    val reason: String,
  ) : WalletMetadataRemediation
}

interface WalletMetadataServerBackupSyncWorker : build.wallet.worker.AppWorker
